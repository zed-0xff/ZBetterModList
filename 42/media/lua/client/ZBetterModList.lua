require "OptionScreens/ModSelector/ModListPanel"

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
end

local orig_applyFilters = ModSelector.ModListPanel.applyFilters
function ModSelector.ModListPanel:applyFilters()
    orig_applyFilters(self)

    if self.filterCombo2.selected == 1 then
        if ZBetterModList.known_mods_after == nil then
            ZBetterModList.known_mods_after = {}
            for _, modData in pairs(self.model.currentMods) do
                local modId = modData.modInfo:getId()
                ZBetterModList.known_mods_after[modId] = true
            end
            writeKnownList(ZBetterModList.known_mods_after)
        end

        return
    end

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
            local modId = modData.modInfo:getId()
            if not ZBetterModList.known_mods_before[modId] then
                table.insert(newTbl, modData)
            end
        end
    end

    self.model.currentMods = newTbl
end
