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

    self.sortCombo.selected = readSortOrder()
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

    -- Sort (persist selection)
    writeSortOrder(self.sortCombo.selected)
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
            self:drawText(ageText, starX - ageWidth - UI_BORDER_SPACING * 2, y + itemPadY, r, g, b, 0.7, UIFont.Small)
        end
    end

    return nextY
end

require "OptionScreens/ModSelector/ModInfoPanelTitle"
require "OptionScreens/ModSelector/ModInfoPanelInteractionParam"

--- Inline comma-separated display for Dependencies / Incompatible panels

local COMMA_WIDTH = getTextManager():MeasureStringX(UIFont.Small, ", ")

function ModInfoPanel.InteractionParam:render()
    self:drawRectBorder(0, 0, self.borderX, self.height, self.borderColor.a, self.borderColor.r, self.borderColor.g, self.borderColor.b)
    self:drawText(self.name, self.borderX - UI_BORDER_SPACING - self.labelWidth, (BUTTON_HGT - FONT_HGT_SMALL) / 2, 0.9, 0.9, 0.9, 0.9, UIFont.Small)

    local x = self.borderX + self.padX
    local y = 2
    for index, val in ipairs(self.modDict) do
        if index > 1 then
            self:drawText(", ", x, y, 0.6, 0.6, 0.6, 0.9, UIFont.Small)
            x = x + COMMA_WIDTH
        end
        self:drawText(val.id, x, y, val.color.r, val.color.g, val.color.b, 0.9, UIFont.Small)
        local hovered = self:isMouseOver() and self:getMouseX() > x and self:getMouseX() < x + val.len
                and self:getMouseY() > y and self:getMouseY() < y + FONT_HGT_SMALL + 1
        if not hovered then
            self:drawRectBorder(x, y + FONT_HGT_SMALL, val.len, 1, 0.9, val.color.r, val.color.g, val.color.b)
        elseif self.pressed then
            if val.available then
                self.parent:setModInfo(val.modInfo)
            else
                local t = luautils.split(val.id, "\\")
                if t[1] ~= "" then
                    activateSteamOverlayToWorkshopItem(t[1])
                end
            end
        end
        x = x + val.len
    end
    self.pressed = false
end

local orig_interactionSetModInfo = ModInfoPanel.InteractionParam.setModInfo
function ModInfoPanel.InteractionParam:setModInfo(modInfo)
    orig_interactionSetModInfo(self, modInfo)
    self:setHeight(BUTTON_HGT)
end

---

local function onWorkshopBtn(workshopID)
    activateSteamOverlayToWorkshopItem(workshopID)
end

local orig_titleCreateChildren = ModInfoPanel.Title.createChildren
function ModInfoPanel.Title:createChildren()
    orig_titleCreateChildren(self)

    local btnW = getTextManager():MeasureStringX(UIFont.Small, getText("UI_modselector_openWorkshopPage")) + UI_BORDER_SPACING * 2
    self.workshopBtn = ISButton:new(self.width - btnW - UI_BORDER_SPACING, UI_BORDER_SPACING + 1, btnW, BUTTON_HGT, getText("UI_modselector_openWorkshopPage"), self, function(self) onWorkshopBtn(self.workshopID) end)
    self.workshopBtn:initialise()
    self.workshopBtn:instantiate()
    self:addChild(self.workshopBtn)
end

local orig_titleSetModInfo = ModInfoPanel.Title.setModInfo
function ModInfoPanel.Title:setModInfo(modInfo)
    orig_titleSetModInfo(self, modInfo)
    local workshopID = getWorkshopID(modInfo)
    self.workshopID = workshopID
    self.workshopBtn:setVisible(workshopID ~= nil)
end

--- Fix incompatible panel height after vanilla overrides it

local orig_updateView = ModInfoPanel.updateView
function ModInfoPanel:updateView(modInfo)
    orig_updateView(self, modInfo)
    self.incompatiblePanel:setHeight(BUTTON_HGT)
end

local orig_recalcSize = ModInfoPanel.recalcSize
function ModInfoPanel:recalcSize()
    orig_recalcSize(self)
    self.incompatiblePanel:setHeight(BUTTON_HGT)
end

--- Tab panel with Info + Files tabs (following MainOptions pattern)

require "OptionScreens/ModSelector/ModSelector"

local orig_modSelectorCreate = ModSelector.create
function ModSelector:create()
    orig_modSelectorCreate(self)

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
end
