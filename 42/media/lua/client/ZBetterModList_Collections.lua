-- Steam Workshop collection state for ZBetterModList.
-- Owns: persistence of tracked collection IDs, async fetch of titles + children
-- via the public Steam Web API (ISteamRemoteStorage/GetCollectionDetails +
-- GetPublishedFileDetails), URL/ID parsing. No UI concerns live here; UI layers
-- drive refresh via Collections.poll() each frame.

local MOD_ID       = "ZBetterModList"
local STORAGE_FILE = MOD_ID .. "_collections.txt"

ZBetterModList = ZBetterModList or {}

local Collections = {
    ids             = {}, -- ordered list of collection IDs (string)
    byId            = {}, -- id -> { title = "", childIds = { [workshopIdStr] = true }, loaded = bool }
    pending         = {}, -- id -> SteamCollection handle (fetch in flight)
    subscribeOnLoad = {}, -- id -> true: subscribe to all children once the current fetch completes
}
ZBetterModList.Collections = Collections

-- Extract a Steam Workshop ID from a URL, or return the input unchanged if it
-- is already a bare numeric ID. Accepts the common shapes:
--   https://steamcommunity.com/sharedfiles/filedetails/?id=1234567890
--   https://steamcommunity.com/workshop/filedetails/?id=1234567890
--   steamcommunity.com/...?id=1234&foo=bar
--   1234567890
function Collections.extractId(input)
    if not input then return nil end
    input = tostring(input):trim()
    if input == "" then return nil end
    return input:match("[?&]id=(%d+)") or input:match("^(%d+)$")
end

local function splitTabs(line)
    local out = {}
    for part in (line .. "\t"):gmatch("([^\t]*)\t") do
        out[#out + 1] = part
    end
    return out
end

local function load()
    Collections.ids = {}
    Collections.byId = {}
    local reader = getFileReader(STORAGE_FILE, false)
    if not reader then return end
    local line = reader:readLine()
    while line do
        line = line:trim()
        if line ~= "" then
            local parts = splitTabs(line)
            local id = parts[1]
            if id and id ~= "" and not Collections.byId[id] then
                local title    = parts[2] or ""
                local childCsv = parts[3] or ""
                local entry    = { title = title, childIds = {}, loaded = childCsv ~= "" }
                for cid in childCsv:gmatch("(%d+)") do
                    entry.childIds[cid] = true
                end
                table.insert(Collections.ids, id)
                Collections.byId[id] = entry
            end
        end
        line = reader:readLine()
    end
    reader:close()
end

local function save()
    local writer = getFileWriter(STORAGE_FILE, true, false)
    if not writer then return end
    for _, id in ipairs(Collections.ids) do
        local entry = Collections.byId[id] or {}
        local csv = {}
        for cid in pairs(entry.childIds or {}) do csv[#csv + 1] = cid end
        table.sort(csv)
        writer:write(id .. "\t" .. (entry.title or "") .. "\t" .. table.concat(csv, ",") .. "\n")
    end
    writer:close()
end

-- Copy title + children from a completed SteamCollection result into a Collection entry.
local function absorbResult(id, result)
    local entry = Collections.byId[id]
    if not entry then return end
    if result then
        local title = result.title
        if title and title ~= "" then entry.title = title end
        entry.childIds = {}
        local children = result.children
        if children then
            for _, cid in pairs(children) do
                if cid and cid ~= "" then entry.childIds[tostring(cid)] = true end
            end
        end
    end
    entry.loaded = true
end

-- Start an async Web API fetch for this collection's title + children. No-op if
-- a fetch is already in flight or SteamCollection is unavailable.
function Collections.fetch(id)
    if not id or Collections.pending[id] then return end
    if not SteamCollection then return end
    local handle = SteamCollection.fetch(id)
    if not handle then return end
    Collections.pending[id] = handle
end

-- Poll all in-flight fetches. Call each frame from the UI layer. Returns true
-- if any collection completed this tick so the caller can refresh UI.
function Collections.poll()
    local anyDone = false
    for id, handle in pairs(Collections.pending) do
        local result = handle:poll()
        if result then
            Collections.pending[id] = nil
            absorbResult(id, result)
            if Collections.subscribeOnLoad[id] then
                Collections.subscribeOnLoad[id] = nil
                Collections.subscribeAll(id)
            end
            anyDone = true
        end
    end
    if anyDone then save() end
    return anyDone
end

-- Add a collection by URL or raw ID. Returns the ID on success, nil otherwise.
-- Does NOT fetch; callers should invoke Collections.fetch(id) afterwards if they
-- want the title / children populated.
function Collections.add(input)
    local id = Collections.extractId(input)
    if not id then return nil end
    if not Collections.byId[id] then
        table.insert(Collections.ids, id)
        Collections.byId[id] = { title = "", childIds = {}, loaded = false }
        save()
    end
    return id
end

function Collections.remove(id)
    if not id or not Collections.byId[id] then return false end
    Collections.byId[id] = nil
    Collections.pending[id] = nil
    Collections.subscribeOnLoad[id] = nil
    for i, v in ipairs(Collections.ids) do
        if v == id then table.remove(Collections.ids, i); break end
    end
    save()
    return true
end

function Collections.contains(collectionId, workshopId)
    if not collectionId or not workshopId then return false end
    local entry = Collections.byId[collectionId]
    return (entry and entry.childIds[tostring(workshopId)]) and true or false
end

-- Human-readable label for the combo box entry. Appends a loading marker
-- whenever a fetch is in flight (initial load or explicit refresh).
function Collections.displayName(id)
    local entry = Collections.byId[id]
    if not entry then return id end
    local pending = Collections.pending[id] ~= nil
    local base
    if entry.title and entry.title ~= "" then
        base = entry.title
    elseif not entry.loaded then
        base = id
    else
        base = id
    end
    if pending then
        return base .. " " .. getText("UI_modselector_collection_loading")
    end
    return base
end

-- Force a re-fetch of an already-tracked collection. Idempotent while a fetch
-- is in flight. Keeps the cached title/children visible until new data arrives.
function Collections.refresh(id)
    if not id or not Collections.byId[id] then return end
    Collections.fetch(id)
end

-- Refetch every tracked collection (e.g. on mod selector open) so titles and
-- children stay fresh without user interaction.
function Collections.refreshAll()
    for _, id in ipairs(Collections.ids) do
        Collections.fetch(id)
    end
end

-- Subscribe to every child workshop item of the given collection. Returns the
-- number of subscribe requests successfully dispatched to Steam (NOT the number
-- of downloads completed — subscriptions resolve asynchronously on Steam's side).
function Collections.subscribeAll(id)
    local entry = id and Collections.byId[id]
    if not entry or not entry.loaded then return 0 end
    if not SteamLuaHelper or not SteamLuaHelper.subscribeToWorkshopItem then return 0 end
    local n = 0
    for cid in pairs(entry.childIds) do
        if SteamLuaHelper.subscribeToWorkshopItem(cid) then n = n + 1 end
    end
    return n
end

-- Add a collection and subscribe to every one of its items. If children are
-- already cached, subscribes immediately; otherwise queues a subscribe-all to
-- fire once the async fetch resolves in Collections.poll().
function Collections.addAndSubscribe(input)
    local id = Collections.add(input)
    if not id then return nil end
    local entry = Collections.byId[id]
    if entry and entry.loaded then
        Collections.subscribeAll(id)
    else
        Collections.subscribeOnLoad[id] = true
        Collections.fetch(id)
    end
    return id
end

load()
