require "OptionScreens/ModSelector/ModListPanel"
require "OptionScreens/ModSelector/ModListBox"

ZBetterModList = ZBetterModList or {
    known_mods_before = nil,
    known_mods_after = nil,
}

local MOD_ID = "ZBetterModList"

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

local function getWorkshopID(modInfo)
    local workshopID = modInfo:getWorkshopID()
    if workshopID and workshopID ~= "" then
        return workshopID
    end
    local dir = modInfo:getDir()
    if dir then
        return string.match(tostring(dir):lower(), "[/\\]108600[/\\](%d+)[/\\]mods[/\\]")
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
    if diff < 60 then return diff .. "s" end
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

local FONT_HGT_SMALL = getTextManager():getFontHeight(UIFont.Small)
local FONT_HGT_MEDIUM = getTextManager():getFontHeight(UIFont.Medium)
local BUTTON_HGT = FONT_HGT_SMALL + 6
local LABEL_HGT = FONT_HGT_MEDIUM + 6
local UI_BORDER_SPACING = 10

local orig_createChildren = ModSelector.ModListPanel.createChildren
function ModSelector.ModListPanel:createChildren()
    orig_createChildren(self)

    local text_showEnabledMods = getText("UI_modselector_showEnabledMods")
    self.enabledModsTickbox:setVisible(false)


    local label = ISLabel:new(UI_BORDER_SPACING+1, self.filterCombo:getBottom() + UI_BORDER_SPACING, LABEL_HGT, getText("UI_modselector_filter"), 1.0, 1.0, 1.0, 1.0, UIFont.Medium, true)
    self.filterPanel:addChild(label)

    local width = BUTTON_HGT + UI_BORDER_SPACING + getTextManager():MeasureStringX(UIFont.Small, getText("UI_modselector_showEnabledMods"))
    self.filterCombo2 = ISComboBox:new(label:getRight() + UI_BORDER_SPACING, label.y, width, BUTTON_HGT, self, self.updateView)
    self.filterCombo2:initialise()

    self.filterCombo2:addOption(getText("UI_modselector_showAllMods"))      -- 1
    self.filterCombo2:addOption(getText("UI_modselector_showEnabledMods"))  -- 2
    self.filterCombo2:addOption(getText("UI_modselector_showDisabledMods")) -- 3
    self.filterCombo2:addOption(getText("UI_modselector_showNewMods"))      -- 4

    self.filterCombo2.selected = 1
    self.filterPanel:addChild(self.filterCombo2)

    local sortLabel = ISLabel:new(self.filterCombo2:getRight() + UI_BORDER_SPACING * 2, label.y, LABEL_HGT, getText("UI_modselector_sortBy"), 1.0, 1.0, 1.0, 1.0, UIFont.Medium, true)
    self.filterPanel:addChild(sortLabel)

    local sortWidth = BUTTON_HGT + UI_BORDER_SPACING + getTextManager():MeasureStringX(UIFont.Small, getText("UI_modselector_sortByDate"))
    self.sortCombo = ISComboBox:new(sortLabel:getRight() + UI_BORDER_SPACING, sortLabel.y, sortWidth, BUTTON_HGT, self, self.updateView)
    self.sortCombo:initialise()

    self.sortCombo:addOption(getText("UI_modselector_sortByName"))  -- 1
    self.sortCombo:addOption(getText("UI_modselector_sortByDate"))  -- 2

    self.sortCombo.selected = 1
    self.filterPanel:addChild(self.sortCombo)
end

local orig_applyFilters = ModSelector.ModListPanel.applyFilters
function ModSelector.ModListPanel:applyFilters()
    orig_applyFilters(self)

    -- Query workshop details for date sorting (once)
    queryWorkshopDetails(self)

    -- Filter
    if self.filterCombo2.selected == 1 then
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
            if self.filterCombo2.selected == 2 then
                if modData.isActive then
                    table.insert(newTbl, modData)
                end
            elseif self.filterCombo2.selected == 3 then
                if not modData.isActive then
                    table.insert(newTbl, modData)
                end
            elseif self.filterCombo2.selected == 4 then
                if not ZBetterModList.known_mods_before[modData.modInfo:getId()] then
                    table.insert(newTbl, modData)
                end
            end
        end
        self.model.currentMods = newTbl
    end

    -- Sort
    if self.sortCombo.selected == 2 then
        table.sort(self.model.currentMods, function(a, b)
            return getModTimeUpdated(a.modInfo) > getModTimeUpdated(b.modInfo)
        end)
    else
        table.sort(self.model.currentMods, function(a, b)
            return a.modInfo:getName():lower() < b.modInfo:getName():lower()
        end)
    end
end

local orig_doDrawItem = ModSelector.ModListBox.doDrawItem
function ModSelector.ModListBox:doDrawItem(y, item, alt)
    local nextY = orig_doDrawItem(self, y, item, alt)

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
            self:drawText(ageText, starX - ageWidth - UI_BORDER_SPACING, y + itemPadY, r, g, b, 0.7, UIFont.Small)
        end
    end

    return nextY
end
