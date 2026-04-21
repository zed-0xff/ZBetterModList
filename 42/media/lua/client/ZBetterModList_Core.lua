--[[
  Shared state, mod metadata helpers, workshop queries, bulk activate,
  navigation stack, collection UI callbacks, and top-bar button styling.
  Loaded by ZBetterModList.lua before the hook modules.
]]

require "ZBetterModList_Collections"

-- Steam UGC query constants (global)
PZ_APP_ID = 108600
k_EUGCQuery_RankedByVote = 0
k_EUGCQuery_RankedByPublicationDate = 1
k_EUGCQuery_RankedByTrend = 3
k_EUGCQuery_RankedByVotesUp = 10
k_EUGCQuery_RankedByLastUpdatedDate = 19
k_EUGCMatchingUGCType_Items = 0
k_EUGCMatchingUGCType_Items_ReadyToUse = 2
k_EUGCMatchingUGCType_UsableInGame = 10

local MOD_ID = "ZBetterModList"
local PZ_ID  = 108600

ZBetterModList = ZBetterModList or {}
ZBetterModList.known_mods_before = ZBetterModList.known_mods_before
ZBetterModList.known_mods_after  = ZBetterModList.known_mods_after
ZBetterModList.known_sids = {}
ZBetterModList.hideWorkshopIds   = ZBetterModList.hideWorkshopIds or {}

local _unsubscribed = false

local MID2SID = {}
local DIR2SID = {}

ZBetterModList.MID2SID = MID2SID
ZBetterModList.DIR2SID = DIR2SID

local logger = zdk.Logger.new("ZBetterModList")

zdk.hook({
    ActiveMods = {
        requiresResetLua = function(orig, ...)
            return orig(...) or _unsubscribed
        end
    },

    _G = {
        getModDirectoryTable = function(orig)
            local full = orig()
            local out = {}
            local n = 0
            for i = 1, #full do
                local path = full[i]
                local hide = false
                for workshopId in pairs(ZBetterModList.hideWorkshopIds) do
                    if path:find("/" .. tostring(PZ_ID) .. "/" .. workshopId .. "/mods/", 1, true) then
                        hide = true
                        break
                    end
                end
                if not hide then
                    n = n + 1
                    out[n] = path
                end
            end
            return out
        end
    }
})

local function parseModInfo(mod_id)
    local reader = getModFileReader(mod_id, "mod.info", false)
    if not reader then return end

    local info = {}
    local line = reader:readLine()
    while line ~= nil do
        line = line:trim():gsub("\t", " ")
        local eq_pos = line:find("=")
        if eq_pos then
            local key   = line:sub(0, eq_pos - 1):trim():lower()
            local value = line:sub(eq_pos + 1):trim()
            info[key] = value
        end

        line = reader:readLine()
    end
    reader:close()
    return info
end

local _mod_info_cache = {}
local function getRawModInfo(mod_id)
    local cached = _mod_info_cache[mod_id]
    if not cached then
        cached = parseModInfo(mod_id)
        _mod_info_cache[mod_id] = cached
    end
    return cached or {}
end

local function isJavaMod(modInfo)
    return (getRawModInfo(modInfo:getId()).javajarfile ~= nil)
end

local function readKnownList()
    local result = {}
    local reader = getFileReader(MOD_ID .. "_known.txt", false)
    if reader then
        local line = reader:readLine()
        while line do
            result[line] = true
            line = reader:readLine()
        end
        reader:close()
    end
    return result
end

local function writeKnownList(list)
    local writer = getFileWriter(MOD_ID .. "_known.txt", true, false)
    if writer then
        for modID, _ in pairs(list) do
            writer:write(modID .. "\n")
        end
        writer:close()
    end
end

if ZBetterModList.known_mods_before == nil then
    ZBetterModList.known_mods_before = readKnownList()
end

local function readSortOrder()
    local reader = getFileReader(MOD_ID .. "_sort.txt", false)
    if reader then
        local line = reader:readLine()
        reader:close()
        return tonumber(line) or 1
    end
    return 1
end

local function writeSortOrder(order)
    local writer = getFileWriter(MOD_ID .. "_sort.txt", true, false)
    if writer then
        writer:write(tostring(order) .. "\n")
        writer:close()
    end
end

local function getWorkshopID(modInfo)
    local workshopID = modInfo:getWorkshopID()
    if workshopID and workshopID ~= "" then
        return workshopID
    end
    local sid = MID2SID[modInfo:getId()]
    if sid then return sid end

    local mod_dir = modInfo:getDir()
    for wdir, sid in pairs(DIR2SID) do
        if luautils.stringStarts(mod_dir, wdir) then
            return sid
        end
    end
    logger:warn_once("getWorkshopID(): failed to get id for %s", modInfo:getId())
    return nil
end
ZBetterModList.getWorkshopID = getWorkshopID

local function getDependentMods(modInfo)
    if not modInfo then return {} end
    local id    = modInfo:getId()
    local out   = {}
    local model = ModSelector.instance and ModSelector.instance.model
    local reqs  = model and model.requirements and model.requirements[id]

    if reqs and reqs.neededFor then
        for depId in pairs(reqs.neededFor) do out[#out + 1] = depId end
    elseif model and model.mods then
        for otherId, data in pairs(model.mods) do
            local req = data.modInfo and data.modInfo:getRequire()
            if req then
                for i = 0, req:size() - 1 do
                    if req:get(i) == id then out[#out + 1] = otherId; break end
                end
            end
        end
    end

    table.sort(out, function(a, b) return a:lower() < b:lower() end)
    return out
end
ZBetterModList.getDependentMods = getDependentMods

local function getModTimeUpdated(modInfo)
    local workshopID = getWorkshopID(modInfo)
    if workshopID and ZBetterModList.timeUpdated then
        return ZBetterModList.timeUpdated[workshopID] or 0
    end
    return 0
end

local function formatAge(timestamp)
    if timestamp == 0 then return "" end
    local diff = os.time() - timestamp
    if diff < 60 then return "< 1m" end
    if diff < 3600 then return math.floor(diff / 60) .. "m" end
    if diff < 86400 then return math.floor(diff / 3600) .. "h" end
    if diff < 86400 * 30 then return math.floor(diff / 86400) .. "d" end
    if diff < 86400 * 365 then return math.floor(diff / (86400 * 30)) .. "M" end
    return math.floor(diff / (86400 * 365)) .. "y"
end

local function queryWorkshopDetails(panel)
    if ZBetterModList.workshopQueryDone then return end
    ZBetterModList.workshopQueryDone = true
    ZBetterModList.timeUpdated = {}

    local workshopIDs = getSteamWorkshopItemIDs()
    if not workshopIDs or workshopIDs:isEmpty() then return end

    querySteamWorkshopItemDetails(workshopIDs, function(panel, status, info)
        if status == "Completed" then
            for i = 1, info:size() do
                local details = info:get(i - 1)
                ZBetterModList.timeUpdated[details:getIDString()] = details:getTimeUpdated()
            end
            if panel.sortCombo and panel.sortCombo.selected == 2 then
                panel:updateView()
            end
        end
    end, panel)
end

local FONT_HGT_SMALL    = getTextManager():getFontHeight(UIFont.Small)
local FONT_HGT_MEDIUM   = getTextManager():getFontHeight(UIFont.Medium)
local BUTTON_HGT        = FONT_HGT_SMALL + 6
local LABEL_HGT         = FONT_HGT_MEDIUM + 6
local UI_BORDER_SPACING = 10

local hasModManager = getActivatedMods():contains("ModManager") or getActivatedMods():contains("\\ModManager")

local Collections = ZBetterModList.Collections

local function updateCollectionButtonsEnable(panel)
    local hasData      = #panel.collectionCombo.options > 1
    local hasSelection = panel.selectedCollectionId ~= nil

    if panel.removeCollectionBtn then
        panel.removeCollectionBtn:setEnable(hasSelection)
        if not hasSelection then
            panel.removeCollectionBtn.borderColor  = {r=1, g=1, b=1, a=0.2}
        end
    end

    if panel.refreshCollectionBtn then
        panel.refreshCollectionBtn:setEnable(hasData)
        if not hasData then
            panel.refreshCollectionBtn.borderColor = {r=1, g=1, b=1, a=0.2}
        end
    end
end

local function rebuildCollectionCombo(panel)
    local combo = panel and panel.collectionCombo
    if not combo then return end

    local prevId = panel.selectedCollectionId
    combo:clear()
    combo:addOptionWithData(getText("UI_modselector_collection_all"), nil)
    local newSelected = 1
    for i, id in ipairs(Collections.ids) do
        combo:addOptionWithData(Collections.displayName(id), id)
        if id == prevId then newSelected = i + 1 end
    end
    combo.selected = newSelected
    panel.selectedCollectionId = combo.options[newSelected] and combo.options[newSelected].data or nil
    updateCollectionButtonsEnable(panel)
end

local function onAddCollectionPrompt(panel, button)
    if ModSelector.instance then ModSelector.instance.addCollectionModal = nil end
    if button.internal ~= "OK" then return end
    local input = button.parent.entry:getText()
    local id = Collections.addAndSubscribe(input)
    if not id then return end

    panel.selectedCollectionId = id
    rebuildCollectionCombo(panel)
    panel:updateView()
end

local function onAddCollectionBtn(panel)
    local w, h = 340, 160
    local modal = ISTextBox:new(
        (getCore():getScreenWidth() - w) / 2,
        (getCore():getScreenHeight() - h) / 2,
        w, h,
        getText("UI_modselector_collection_add_prompt"),
        "", panel, onAddCollectionPrompt)
    modal:initialise()
    modal:addToUIManager()
    if ModSelector.instance then ModSelector.instance.addCollectionModal = modal end
end

local function onRemoveCollectionBtn(panel)
    local id = panel.selectedCollectionId
    if not id then return end
    Collections.remove(id)
    panel.selectedCollectionId = nil
    rebuildCollectionCombo(panel)
    panel:updateView()
end

local function onCollectionComboChange(panel)
    local combo = panel.collectionCombo
    local opt = combo.options[combo.selected]
    panel.selectedCollectionId = opt and opt.data or nil
    updateCollectionButtonsEnable(panel)
    panel:updateView()
end

local function onRefreshCollectionBtn(panel)
    local id = panel.selectedCollectionId
    if not id then return end
    Collections.refresh(id)
    rebuildCollectionCombo(panel)
end

local function topoSortMods(snapshot)
    local deps  = {}
    local index = {}
    for _, mi in ipairs(snapshot) do
        local id = mi:getId()
        index[id] = mi
        deps[id]  = {}
    end

    local function addEdge(beforeId, afterId)
        if beforeId == afterId then return end
        if not index[beforeId] or not index[afterId] then return end
        deps[afterId][beforeId] = true
    end

    local function eachId(list, fn)
        if not list then return end
        for i = 0, list:size() - 1 do fn(list:get(i)) end
    end

    for _, mi in ipairs(snapshot) do
        local id = mi:getId()
        eachId(mi:getRequire(),    function(other) addEdge(other, id) end)
        eachId(mi:getLoadAfter(),  function(other) addEdge(other, id) end)
        eachId(mi:getLoadBefore(), function(other) addEdge(id, other) end)
    end

    local placed, result = {}, {}
    local changed = true
    while changed do
        changed = false
        for _, mi in ipairs(snapshot) do
            local id = mi:getId()
            if not placed[id] then
                local ready = true
                for depId in pairs(deps[id]) do
                    if not placed[depId] then ready = false; break end
                end
                if ready then
                    placed[id] = true
                    result[#result + 1] = mi
                    changed = true
                end
            end
        end
    end
    for _, mi in ipairs(snapshot) do
        if not placed[mi:getId()] then result[#result + 1] = mi end
    end
    return result
end

local function bulkActivate(activate)
    local sel = ModSelector.instance
    if not sel or not sel.model or not sel.model.currentMods then return end

    local snapshot = {}
    for _, modData in ipairs(sel.model.currentMods) do
        snapshot[#snapshot + 1] = modData.modInfo
    end

    local ordered = topoSortMods(snapshot)
    if not activate then
        local rev = {}
        for i = #ordered, 1, -1 do rev[#rev + 1] = ordered[i] end
        ordered = rev
    end

    for _, modInfo in ipairs(ordered) do
        sel.model:forceActivateMods(modInfo, activate)
    end

    if sel.modListPanel and sel.modListPanel.updateView then
        sel.modListPanel:updateView()
    end
end

local function onEnableAllBtn()  bulkActivate(true)  end
local function onDisableAllBtn() bulkActivate(false) end

local navHistory    = {}
local NAV_HISTORY_MAX = 64
local navigatingBack  = false

local function navClear()
    for i = #navHistory, 1, -1 do navHistory[i] = nil end
end

local function navPush(id)
    if not id or navigatingBack then return end
    navHistory[#navHistory + 1] = id
    if #navHistory > NAV_HISTORY_MAX then table.remove(navHistory, 1) end
end

local function navBack(sel)
    local id = table.remove(navHistory)
    if not id or not sel or not sel.modInfoPanel then return end
    local data = sel.model and sel.model.mods and sel.model.mods[id]
    if not data or not data.modInfo then return end
    navigatingBack = true
    sel.modInfoPanel:updateView(data.modInfo)
    navigatingBack = false
end

local function isSelTypingTarget(sel)
    if sel.addCollectionModal then return true end
    local search = sel.modListPanel and sel.modListPanel.searchEntry
    if search and search.isFocused and search:isFocused() then return true end
    return false
end

local function updateLists()
    local workshopIDs = getSteamWorkshopItemIDs()
    if not workshopIDs or workshopIDs:isEmpty() then return end

    for i=0, workshopIDs:size()-1 do
        local sid = workshopIDs:get(i)
        ZBetterModList.known_sids[sid] = true

        local mods = getSteamWorkshopItemMods(sid)
        for j=0, mods:size()-1 do
            local mod = mods:get(j)
            MID2SID[mod:getId()] = sid
        end
    end

    local items = getSteamWorkshopStagedItems()
    for i=0, items:size()-1 do
        local item = items:get(i)
        DIR2SID[item:getContentFolder()] = item:getID()
        ZBetterModList.known_sids[item:getID()] = true
    end
end

local TOP_BTN_PADDING = 32 + UI_BORDER_SPACING * 2

local function styleTopBarButton(btn, tooltipText)
    btn:initialise()
    btn:instantiate()
    btn:setAnchorLeft(true)
    btn:setAnchorRight(false)
    btn:setAnchorTop(true)
    btn:setAnchorBottom(false)
    btn.borderColor = {r = 1, g = 1, b = 1, a = 0.1}
    btn:setFont(UIFont.Small)
    btn:ignoreWidthChange()
    btn:ignoreHeightChange()
    btn.tooltip = tooltipText
end

local function markUnsubscribed()
    _unsubscribed = true
end

local SHOW_MOD_OPTIONS = { "All", "Enabled", "Disabled", "Java", "New" }
local SORT_OPTIONS     = { "Name", "Date" }

--[[ Shared bindings for hook modules (single source of truth). ]]
ZBetterModList._I = {
    SHOW_MOD_OPTIONS = SHOW_MOD_OPTIONS,
    SORT_OPTIONS     = SORT_OPTIONS,
    Collections      = Collections,
    hasModManager    = hasModManager,

    FONT_HGT_SMALL   = FONT_HGT_SMALL,
    BUTTON_HGT       = BUTTON_HGT,
    LABEL_HGT        = LABEL_HGT,
    UI_BORDER_SPACING = UI_BORDER_SPACING,
    TOP_BTN_PADDING  = TOP_BTN_PADDING,

    readSortOrder    = readSortOrder,
    writeSortOrder   = writeSortOrder,
    writeKnownList   = writeKnownList,

    updateCollectionButtonsEnable = updateCollectionButtonsEnable,
    rebuildCollectionCombo = rebuildCollectionCombo,
    onAddCollectionPrompt = onAddCollectionPrompt,
    onAddCollectionBtn = onAddCollectionBtn,
    onRemoveCollectionBtn = onRemoveCollectionBtn,
    onCollectionComboChange = onCollectionComboChange,
    onRefreshCollectionBtn = onRefreshCollectionBtn,

    updateLists      = updateLists,
    isJavaMod        = isJavaMod,
    getWorkshopID    = getWorkshopID,
    getRawModInfo    = getRawModInfo,
    queryWorkshopDetails = queryWorkshopDetails,
    getModTimeUpdated = getModTimeUpdated,
    formatAge        = formatAge,

    onEnableAllBtn   = onEnableAllBtn,
    onDisableAllBtn  = onDisableAllBtn,

    navHistory       = navHistory,
    navClear         = navClear,
    navPush          = navPush,
    navBack          = navBack,
    isSelTypingTarget = isSelTypingTarget,

    styleTopBarButton = styleTopBarButton,
    markUnsubscribed = markUnsubscribed,
}
