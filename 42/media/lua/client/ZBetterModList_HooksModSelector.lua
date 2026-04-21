--[[ ModSelector: back navigation key, Enable/Disable all, Info + Files tab panel. ]]

require "OptionScreens/ModSelector/ModSelector"

local I = ZBetterModList._I

local function onKeyPressed(key)
    if key == Keyboard.KEY_BACK and not I.isSelTypingTarget(ModSelector.instance) then
        I.navBack(ModSelector.instance)
    end
end

zdk.hook({
    ModSelector = {
        setVisible = function(orig, self, visible, ...)
            orig(self, visible, ...)
            if visible then
                Events.OnKeyPressed.Add(onKeyPressed)
            else
                Events.OnKeyPressed.Remove(onKeyPressed)
            end
        end,

        create = function(orig, self)
            orig(self)

            I.navClear()

            local enLabel  = getText("UI_modselector_enableAll")
            local disLabel = getText("UI_modselector_disableAll")
            local enW      = I.TOP_BTN_PADDING + getTextManager():MeasureStringX(UIFont.Small, enLabel)
            local disW     = I.TOP_BTN_PADDING + getTextManager():MeasureStringX(UIFont.Small, disLabel)
            local btnY     = I.UI_BORDER_SPACING + 1

            self.enableAllBtn = ISButton:new(I.UI_BORDER_SPACING + 1, btnY, enW, I.BUTTON_HGT, enLabel, self, I.onEnableAllBtn)
            I.styleTopBarButton(self.enableAllBtn, getText("UI_modselector_enableAllTooltip"))
            self:addChild(self.enableAllBtn)

            self.disableAllBtn = ISButton:new(self.enableAllBtn:getRight() + I.UI_BORDER_SPACING, btnY, disW, I.BUTTON_HGT, disLabel, self, I.onDisableAllBtn)
            I.styleTopBarButton(self.disableAllBtn, getText("UI_modselector_disableAllTooltip"))
            self:addChild(self.disableAllBtn)

            local origInfoPanel = self.modInfoPanel
            local left = origInfoPanel:getX()
            local top = origInfoPanel:getY()
            local panelW = origInfoPanel:getWidth()
            local panelH = origInfoPanel:getHeight()

            local tabPanel = ISTabPanel:new(left, top, panelW, panelH)
            tabPanel:initialise()
            tabPanel:setAnchorBottom(true)
            tabPanel:setAnchorRight(true)
            tabPanel:setEqualTabWidth(false)
            tabPanel.tabPadX = 20
            tabPanel.model = self.model

            self:removeChild(origInfoPanel)
            origInfoPanel:setX(0)
            origInfoPanel:setWidth(panelW)
            origInfoPanel:setHeight(panelH - tabPanel.tabHeight)
            origInfoPanel:setAnchorLeft(true)
            origInfoPanel:setAnchorRight(true)
            origInfoPanel:setAnchorTop(true)
            origInfoPanel:setAnchorBottom(true)
            tabPanel:addView(getText("UI_modselector_tabInfo"), origInfoPanel)

            local fileListBox = ISScrollingListBox:new(0, 0, panelW, panelH - tabPanel.tabHeight)
            fileListBox:initialise()
            fileListBox:setAnchorLeft(true)
            fileListBox:setAnchorRight(true)
            fileListBox:setAnchorTop(true)
            fileListBox:setAnchorBottom(true)
            fileListBox.font = UIFont.Small
            fileListBox.itemheight = I.BUTTON_HGT
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
                self2:drawText(item.text, I.UI_BORDER_SPACING, y + (I.BUTTON_HGT - I.FONT_HGT_SMALL) / 2, c.r, c.g, c.b, 0.9, UIFont.Small)
                return y + self2.itemheight
            end
            tabPanel:addView(getText("UI_modselector_tabFiles"), fileListBox)

            self:addChild(tabPanel)
            tabPanel:setVisible(false)

            self.modInfoTabPanel = tabPanel
            self.fileListBox = fileListBox

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
        end,
    }
})
