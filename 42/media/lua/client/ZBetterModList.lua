require "OptionScreens/ModSelector/ModListPanel"
require "OptionScreens/ModSelector/ModListBox"

local SHOW_MOD_OPTIONS = { "All", "Enabled", "Disabled", "Java", "New" }
local SORT_OPTIONS     = { "Name", "Date" }

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

ZBetterModList = ZBetterModList or {
    known_mods_before = nil,
    known_mods_after = nil,
    -- Workshop IDs to hide from getModDirectoryTable (unsubscribed this session). Paths containing "/108600/<id>/mods/" are excluded.
    hideWorkshopIds = {},
}

local _unsubscribed = false

-- Override getModDirectoryTable so unsubscribed workshop mod paths are removed from the list.
zdk.hook({
    ActiveMods = {
        -- force reset lua after any workshop unsubscription
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

-- parse "mod.info" file
-- returns a table of key-value pairs (keys are lowercased). Returns nil if no mod.info or error.
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

-- for some reason vanilla getWorkshopID() does not always work (returns empty string)
local function getWorkshopID(modInfo)
    local workshopID = modInfo:getWorkshopID()
    if workshopID and workshopID ~= "" then
        return workshopID
    end
    local dir = modInfo:getDir()
    if dir then
        return string.match(tostring(dir):lower(), "[/\\]" .. tostring(PZ_ID) .. "[/\\](%d+)[/\\]mods[/\\]")
    end
end

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

---

local FONT_HGT_SMALL    = getTextManager():getFontHeight(UIFont.Small)
local FONT_HGT_MEDIUM   = getTextManager():getFontHeight(UIFont.Medium)
local BUTTON_HGT        = FONT_HGT_SMALL + 6
local LABEL_HGT         = FONT_HGT_MEDIUM + 6
local UI_BORDER_SPACING = 10

local hasModManager = getActivatedMods():contains("ModManager") or getActivatedMods():contains("\\ModManager")

if not hasModManager then
    zdk.hook({
        [ModSelector.ModListPanel] = {
            createChildren = function(orig, self)
                orig(self)

                if not self.enabledModsTickbox or not self.filterPanel then return end

                self.enabledModsTickbox:setVisible(false)

                local label = ISLabel:new(UI_BORDER_SPACING+1, self.filterCombo:getBottom() + UI_BORDER_SPACING, LABEL_HGT, getText("UI_modselector_show"), 1.0, 1.0, 1.0, 1.0, UIFont.Medium, true)
                self.filterPanel:addChild(label)

                self.showCombo = ISComboBox:new(self.filterCombo:getX(), label.y, self.filterCombo:getWidth(), BUTTON_HGT, self, self.updateView)
                self.showCombo:initialise()

                for _, option in ipairs(SHOW_MOD_OPTIONS) do
                    self.showCombo:addOption(getText("UI_modselector_show" .. option))
                end

                self.showCombo.selected = 1
                self.filterPanel:addChild(self.showCombo)

                local sortLabel = ISLabel:new(self.showCombo:getRight() + UI_BORDER_SPACING * 2, label.y, LABEL_HGT, getText("UI_modselector_sortBy"), 1.0, 1.0, 1.0, 1.0, UIFont.Medium, true)
                self.filterPanel:addChild(sortLabel)

                local sortWidth = BUTTON_HGT + UI_BORDER_SPACING + getTextManager():MeasureStringX(UIFont.Small, getText("UI_modselector_sortByDate"))
                self.sortCombo = ISComboBox:new(sortLabel:getRight() + UI_BORDER_SPACING, sortLabel.y, sortWidth, BUTTON_HGT, self, self.updateView)
                self.sortCombo:initialise()

                for _, option in ipairs(SORT_OPTIONS) do
                    self.sortCombo:addOption(getText("UI_modselector_sortBy" .. option))
                end

                self.sortCombo.selected = readSortOrder()
                self.filterPanel:addChild(self.sortCombo)
            end,

            applyFilters = function(orig, self)
                orig(self)

                if not self.showCombo then return end

                -- Query workshop details for date sorting (once)
                queryWorkshopDetails(self)

                -- Filter
                local showMode = SHOW_MOD_OPTIONS[self.showCombo.selected]
                if showMode == "All" then
                    if ZBetterModList.known_mods_after == nil then
                        ZBetterModList.known_mods_after = {}
                        for _, modData in pairs(self.model.currentMods) do
                            ZBetterModList.known_mods_after[modData.modInfo:getId()] = true
                        end
                        writeKnownList(ZBetterModList.known_mods_after)
                    end
                else
                    local newTbl = {}
                    for _, modData in pairs(self.model.currentMods) do
                        if showMode == "Enabled" then
                            if modData.isActive then
                                table.insert(newTbl, modData)
                            end
                        elseif showMode == "Disabled" then
                            if not modData.isActive then
                                table.insert(newTbl, modData)
                            end
                        elseif showMode == "Java" then
                            if isJavaMod(modData.modInfo) then
                                table.insert(newTbl, modData)
                            end
                        elseif showMode == "New" then
                            if not ZBetterModList.known_mods_before[modData.modInfo:getId()] then
                                table.insert(newTbl, modData)
                            end
                        end
                    end
                    self.model.currentMods = newTbl
                end

                -- Sort (persist selection)
                writeSortOrder(self.sortCombo.selected)
                local sortMode = SORT_OPTIONS[self.sortCombo.selected]
                if sortMode == "Date" then
                    table.sort(self.model.currentMods, function(a, b)
                        return getModTimeUpdated(a.modInfo) > getModTimeUpdated(b.modInfo)
                    end)
                else
                    table.sort(self.model.currentMods, function(a, b)
                        return a.modInfo:getName():lower() < b.modInfo:getName():lower()
                    end)
                end
            end,
        },

        [ModSelector.ModListBox] = {
            doDrawItem = function(orig, self, y, item, ...)
                local nextY = orig(self, y, item, ...)

                local timeUpdated = getModTimeUpdated(item.item.modInfo)
                if timeUpdated > 0 then
                    local ageText = formatAge(timeUpdated)
                    if ageText ~= "" then
                        local ageWidth = getTextManager():MeasureStringX(UIFont.Small, ageText)
                        local starX = self:getWidth() - UI_BORDER_SPACING - BUTTON_HGT - 1
                        local height = nextY - y
                        local itemPadY = (height - FONT_HGT_SMALL) / 2
                        local diff = os.time() - timeUpdated
                        local r, g, b = 0.6, 0.6, 0.6
                        if diff < 86400 * 2 then
                            r, g, b = 0.4, 0.85, 0.4
                        elseif diff > 86400 * 180 then
                            r, g, b = 0.7, 0.5, 0.3
                        end
                        self:drawText(ageText, starX - ageWidth - UI_BORDER_SPACING * 2, y + itemPadY, r, g, b, 0.7, UIFont.Small)
                    end
                end

                return nextY
            end,
        },
    }) -- zdk.hook
end -- if not ModManager

require "OptionScreens/ModSelector/ModInfoPanelTitle"
--require "OptionScreens/ModSelector/ModInfoPanelInteractionParam"

--- Inline comma-separated display for Dependencies / Incompatible panels

--local COMMA_WIDTH = getTextManager():MeasureStringX(UIFont.Small, ", ")
--
--function ModInfoPanel.InteractionParam:render()
--    self:drawRectBorder(0, 0, self.borderX, self.height, self.borderColor.a, self.borderColor.r, self.borderColor.g, self.borderColor.b)
--    self:drawText(self.name, self.borderX - UI_BORDER_SPACING - self.labelWidth, (BUTTON_HGT - FONT_HGT_SMALL) / 2, 0.9, 0.9, 0.9, 0.9, UIFont.Small)
--
--    local x = self.borderX + self.padX
--    local y = 2
--    for index, val in ipairs(self.modDict) do
--        if index > 1 then
--            self:drawText(", ", x, y, 0.6, 0.6, 0.6, 0.9, UIFont.Small)
--            x = x + COMMA_WIDTH
--        end
--        self:drawText(val.id, x, y, val.color.r, val.color.g, val.color.b, 0.9, UIFont.Small)
--        local hovered = self:isMouseOver() and self:getMouseX() > x and self:getMouseX() < x + val.len
--                and self:getMouseY() > y and self:getMouseY() < y + FONT_HGT_SMALL + 1
--        if not hovered then
--            self:drawRectBorder(x, y + FONT_HGT_SMALL, val.len, 1, 0.9, val.color.r, val.color.g, val.color.b)
--        elseif self.pressed then
--            if val.available then
--                self.parent:setModInfo(val.modInfo)
--            else
--                local t = luautils.split(val.id, "\\")
--                if t[1] ~= "" then
--                    activateSteamOverlayToWorkshopItem(t[1])
--                end
--            end
--        end
--        x = x + val.len
--    end
--    self.pressed = false
--end
--
--zdk.hook({
--    [ModInfoPanel.InteractionParam] = {
--        setModInfo = function(orig, self, ...)
--            orig(self, ...)
--            self:setHeight(BUTTON_HGT)
--        end
--    }
--})

---

local function onWorkshopBtn(workshopID)
    activateSteamOverlayToWorkshopItem(workshopID)
end

-- Returns true if any mod in this workshop package is currently enabled (so unsubscribe should be blocked).
local function isAnyModInPackageEnabled(workshopID, currentModInfo)
    if not workshopID or not getActivatedMods then return false end
    local activeModIDs = getActivatedMods()
    if not activeModIDs or activeModIDs:size() == 0 then return false end

    if getSteamWorkshopItemMods then
        local mods = getSteamWorkshopItemMods(workshopID)
        if mods and not mods:isEmpty() then
            for i = 1, mods:size() do
                local modID = mods:get(i - 1)
                for j = 1, activeModIDs:size() do
                    if activeModIDs:get(j - 1) == modID then return true end
                end
            end
        end
    end

    if currentModInfo then
        local thisID = currentModInfo:getId()
        for j = 1, activeModIDs:size() do
            if activeModIDs:get(j - 1) == thisID then return true end
        end
    end
    return false
end

local function onUnsubscribeBtn(panel)
    local workshopID = panel.workshopID
    if not workshopID or not SteamLuaHelper then return end
    if isAnyModInPackageEnabled(workshopID, panel.modInfo) then
        return
    end
    local ok = SteamLuaHelper.unsubscribeFromWorkshopItem(workshopID)
    if not ok then return end

    _unsubscribed = true
    ZBetterModList.hideWorkshopIds[workshopID] = true
    if ModSelector.instance then
        ModSelector.instance:reloadMods()
        ModSelector.instance.modInfoPanel:setVisible(false)
    end
end

zdk.hook({
    ModInfoPanel = {
        -- add javaJarFile and javaPkgName to modInfoParams for title panel
        new = function(orig, self, ...)
            local o = orig(self, ...)
            if type(o.modInfoParams) == "table" then
                table.insert(o.modInfoParams, "javaJarFile")
                table.insert(o.modInfoParams, "javaPkgName")
            end
            return o
        end,
    },
    [ModInfoPanel.Param] = {
        -- render values of javaJarFile and javaPkgName modInfoParams
        render = function(orig, self, ...)
            orig(self, ...)

            local color = { r = 0.9, g = 0.4, b = 0.9, a = 0.9 }
            local mod_id = self.modInfo:getId()
            if self.type == "javaJarFile" then
                local val = self.modInfo and getRawModInfo(mod_id).javajarfile
                if val then
                    local font = UIFont.Small
                    local tm = getTextManager()
                    local x = self.borderX + UI_BORDER_SPACING
                    local y = 2

                    self:drawText(val, x, y, color.r, color.g, color.b, color.a, font)
                    x = x + tm:MeasureStringX(font, val)

                    local status = ZombieBuddy and ZombieBuddy.getJavaModStatus and ZombieBuddy.getJavaModStatus(mod_id)
                    if status then
                        local def  = { r = 0.9, g = 0.9, b = 0.9, a = 0.9 }
                        local g    = getCore():getGoodHighlitedColor()
                        local good = { r = g:getR(), g = g:getG(), b = g:getB(), a = 0.9 }
                        local b    = getCore():getBadHighlitedColor()
                        local bad  = { r = b:getR(), g = b:getG(), b = b:getB(), a = 0.9 }

                        local function seg(t, c)
                            self:drawText(t, x, y, c.r, c.g, c.b, c.a, font)
                            x = x + tm:MeasureStringX(font, t)
                        end

                        seg("   ", def)
                        if status.loaded then seg("loaded", good) else seg("blocked", bad) end
                        if status.decision then
                            seg(", ", def)
                            if status.decision == "yes" then seg("allow", good) else seg("deny", bad) end
                            seg(", ", def)
                            seg(status.persisted and "persisted" or "session", def)
                        end
                    end
                end
            elseif self.type == "javaPkgName" then
                local val = self.modInfo and getRawModInfo(mod_id).javapkgname
                if val then
                    self:drawText(val, self.borderX + UI_BORDER_SPACING, 2, color.r, color.g, color.b, color.a, UIFont.Small)
                end
            end
        end,
    },
    [ModInfoPanel.Title] = {
        -- add 'unsubscribe' and 'workshop page' buttons to title panel
        createChildren = function(orig, self)
            orig(self)

            local workshopBtnW = getTextManager():MeasureStringX(UIFont.Small, getText("UI_modselector_openWorkshopPage")) + UI_BORDER_SPACING * 2
            local unsubLabel = "Unsubscribe"
            local unsubBtnW = getTextManager():MeasureStringX(UIFont.Small, unsubLabel) + UI_BORDER_SPACING * 2

            self.unsubscribeBtn = ISButton:new(self.width - workshopBtnW - UI_BORDER_SPACING - unsubBtnW - UI_BORDER_SPACING, UI_BORDER_SPACING + 1, unsubBtnW, BUTTON_HGT, unsubLabel, self, onUnsubscribeBtn)
            self.unsubscribeBtn:initialise()
            self.unsubscribeBtn:instantiate()
            self.unsubscribeBtn:enableCancelColor()
            self:addChild(self.unsubscribeBtn)

            self.workshopBtn = ISButton:new(self.width - workshopBtnW - UI_BORDER_SPACING, UI_BORDER_SPACING + 1, workshopBtnW, BUTTON_HGT, getText("UI_modselector_openWorkshopPage"), self, function(self) onWorkshopBtn(self.workshopID) end)
            self.workshopBtn:initialise()
            self.workshopBtn:instantiate()
            self:addChild(self.workshopBtn)
        end,

        -- set visibility and enabled state of workshop/unsubscribe buttons
        setModInfo = function(orig, self, modInfo)
            orig(self, modInfo)

            self.modInfo = modInfo
            local workshopID = getWorkshopID(modInfo)
            self.workshopID = workshopID
            local visible = workshopID ~= nil

            self.workshopBtn:setVisible(visible)

            if self.unsubscribeBtn then
                self.unsubscribeBtn:setVisible(visible)
                local canUnsubscribe = visible and not isAnyModInPackageEnabled(workshopID, modInfo)
                self.unsubscribeBtn:setEnable(canUnsubscribe)
            end
        end,
    }
})

--- Fix incompatible panel height after vanilla overrides it

--zdk.hook({
--    ModInfoPanel = {
--        updateView = function(orig, self, ...)
--            orig(self, ...)
--            self.incompatiblePanel:setHeight(BUTTON_HGT)
--        end,
--
--        recalcSize = function(orig, self, ...)
--            orig(self, ...)
--            self.incompatiblePanel:setHeight(BUTTON_HGT)
--        end,
--    }
--})

--- Tab panel with Info + Files tabs (following MainOptions pattern)

require "OptionScreens/ModSelector/ModSelector"

zdk.hook({
    ModSelector = {
        create = function(orig, self)
            orig(self)

            local origInfoPanel = self.modInfoPanel
            local left = origInfoPanel:getX()
            local top = origInfoPanel:getY()
            local panelW = origInfoPanel:getWidth()
            local panelH = origInfoPanel:getHeight()

            -- Create tab panel (same pattern as MainOptions.lua)
            local tabPanel = ISTabPanel:new(left, top, panelW, panelH)
            tabPanel:initialise()
            tabPanel:setAnchorBottom(true)
            tabPanel:setAnchorRight(true)
            tabPanel:setEqualTabWidth(false)
            tabPanel.tabPadX = 20
            tabPanel.model = self.model

            -- Info tab: re-parent origInfoPanel into tab (like MainOptions:addPage)
            self:removeChild(origInfoPanel)
            origInfoPanel:setX(0)
            origInfoPanel:setWidth(panelW)
            origInfoPanel:setHeight(panelH - tabPanel.tabHeight)
            origInfoPanel:setAnchorLeft(true)
            origInfoPanel:setAnchorRight(true)
            origInfoPanel:setAnchorTop(true)
            origInfoPanel:setAnchorBottom(true)
            tabPanel:addView(getText("UI_modselector_tabInfo"), origInfoPanel)

            -- Files tab: scrollable file list
            local fileListBox = ISScrollingListBox:new(0, 0, panelW, panelH - tabPanel.tabHeight)
            fileListBox:initialise()
            fileListBox:setAnchorLeft(true)
            fileListBox:setAnchorRight(true)
            fileListBox:setAnchorTop(true)
            fileListBox:setAnchorBottom(true)
            fileListBox.font = UIFont.Small
            fileListBox.itemheight = BUTTON_HGT
            local fileColors = {
                lua  = {r = 0.9,  g = 0.85, b = 0.45},
                txt  = {r = 0.95, g = 0.95, b = 0.95},
                info = {r = 0.95, g = 0.95, b = 0.95},
                png  = {r = 0.65, g = 0.9,  b = 0.65},
                jpg  = {r = 0.65, g = 0.9,  b = 0.65},
            }
            local defaultFileColor = {r = 0.7, g = 0.7, b = 0.7}
            fileListBox.doDrawItem = function(self2, y, item, alt)
                local ext = string.match(item.text, "%.([^%.]+)$")
                local c = (ext and fileColors[string.lower(ext)]) or defaultFileColor
                self2:drawText(item.text, UI_BORDER_SPACING, y + (BUTTON_HGT - FONT_HGT_SMALL) / 2, c.r, c.g, c.b, 0.9, UIFont.Small)
                return y + self2.itemheight
            end
            tabPanel:addView(getText("UI_modselector_tabFiles"), fileListBox)

            self:addChild(tabPanel)
            tabPanel:setVisible(false)

            self.modInfoTabPanel = tabPanel
            self.fileListBox = fileListBox

            -- Replace modInfoPanel ref so vanilla setVisible/updateView go through proxy
            self.modInfoPanel = tabPanel

            tabPanel.updateView = function(tabSelf, modInfo)
                origInfoPanel:updateView(modInfo)
                tabSelf:setVisible(true)

                fileListBox:clear()
                local fileCount = 0
                if ModListHelper then
                    local javaList = ModListHelper.listFiles(modInfo:getId())
                    fileCount = javaList:size()
                    for i = 0, fileCount - 1 do
                        fileListBox:addItem(javaList:get(i), javaList:get(i))
                    end
                end
                local filesTabName = getText("UI_modselector_tabFiles") .. " (" .. fileCount .. ")"
                tabPanel.viewList[2].name = filesTabName
                tabPanel.viewList[2].tabWidth = getTextManager():MeasureStringX(UIFont.Small, filesTabName) + tabPanel.tabPadX
            end
        end -- create
    } -- ModSelector
}) -- zdk.hook
