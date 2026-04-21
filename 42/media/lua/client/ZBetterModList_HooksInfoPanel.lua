--[[ ModInfoPanel, InteractionParam (Dependents), Param, Title zdk hooks. ]]

require "OptionScreens/ModSelector/ModInfoPanelTitle"
require "OptionScreens/ModSelector/ModInfoPanelInteractionParam"

local I = ZBetterModList._I

local DEFAULT_COLOR = { r = 0.9, g = 0.9, b = 0.9, a = 0.9 }
local JAVA_COLOR    = { r = 0.9, g = 0.4, b = 0.9, a = 0.9 }

local _ghc, _bhc
local function ghc()
    if not _ghc then
        local c = getCore():getGoodHighlitedColor()
        _ghc = { r = c:getR(), g = c:getG(), b = c:getB(), a = 0.9 }
    end
    return _ghc
end
local function bhc()
    if not _bhc then
        local c = getCore():getBadHighlitedColor()
        _bhc = { r = c:getR(), g = c:getG(), b = c:getB(), a = 0.9 }
    end
    return _bhc
end

local function paramCursor(param)
    local font = UIFont.Small
    local tm   = getTextManager()
    local x    = param.borderX + I.UI_BORDER_SPACING
    local y    = 2
    return function(text, color)
        param:drawText(text, x, y, color.r, color.g, color.b, color.a, font)
        x = x + tm:MeasureStringX(font, text)
    end
end

local paramRenderers = {
    javaJarFile = function(param, seg)
        local mod_id = param.modInfo:getId()
        local val    = I.getRawModInfo(mod_id).javajarfile
        if not val then return end
        seg(val, JAVA_COLOR)

        local status = ZombieBuddy and ZombieBuddy.getJavaModStatus and ZombieBuddy.getJavaModStatus(mod_id)
        if not status then return end
        seg("   ", DEFAULT_COLOR)
        seg(status.loaded and "loaded" or "blocked", status.loaded and ghc() or bhc())
        if status.decision then
            seg(", ", DEFAULT_COLOR)
            seg(status.decision == "yes" and "allow" or "deny", status.decision == "yes" and ghc() or bhc())
            seg(", ", DEFAULT_COLOR)
            seg(status.persisted and "persisted" or "session", DEFAULT_COLOR)
        end
    end,

    javaPkgName = function(param, seg)
        local val = I.getRawModInfo(param.modInfo:getId()).javapkgname
        if val then seg(val, JAVA_COLOR) end
    end,
}

local function onWorkshopBtn(workshopID)
    activateSteamOverlayToWorkshopItem(workshopID)
end

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

local function onTitleNavBackBtn()
    local sel = ModSelector.instance
    if sel then I.navBack(sel) end
end

local function onUnsubscribeBtn(panel)
    local workshopID = panel.workshopID
    if not workshopID or not SteamLuaHelper then return end
    if isAnyModInPackageEnabled(workshopID, panel.modInfo) then
        return
    end
    local ok = SteamLuaHelper.unsubscribeFromWorkshopItem(workshopID)
    if not ok then return end

    I.markUnsubscribed()
    ZBetterModList.hideWorkshopIds[workshopID] = true
    if ModSelector.instance then
        ModSelector.instance:reloadMods()
        ModSelector.instance.modInfoPanel:setVisible(false)
    end
end

zdk.hook({
    ModInfoPanel = {
        new = function(orig, self, ...)
            local o = orig(self, ...)
            if type(o.modInfoParams) == "table" then
                for name in pairs(paramRenderers) do
                    table.insert(o.modInfoParams, name)
                end
            end
            return o
        end,

        createChildren = function(orig, self)
            orig(self)
            self.dependentsPanel = ModInfoPanel.InteractionParam:new(0, self.incompatiblePanel:getBottom() - 1, self.width, "Dependents")
            self.dependentsPanel:initialise()
            self.dependentsPanel:instantiate()
            self:addChild(self.dependentsPanel)
        end,

        updateView = function(orig, self, modInfo)
            if self.modInfo and modInfo and self.modInfo:getId() ~= modInfo:getId() then
                I.navPush(self.modInfo:getId())
            end
            orig(self, modInfo)
            if not self.dependentsPanel then return end
            self.dependentsPanel:setModInfo(modInfo)
            local n = #self.incompatiblePanel.modDict
            self.incompatiblePanel:setHeight(n == 0 and I.BUTTON_HGT or n * I.BUTTON_HGT)
            self.dependentsPanel:setY(self.incompatiblePanel:getBottom() - 1)
            self.dependentsPanel:setHeight(I.BUTTON_HGT)
        end,
    },
    [ModInfoPanel.InteractionParam] = {
        initialise = function(orig, self)
            orig(self)
            if self.type ~= "Dependents" then return end
            self.tooltipUI = ISToolTip:new()
            self.tooltipUI:initialise()
            self.tooltipUI:setOwner(self)
        end,

        update = function(orig, self)
            orig(self)
            if self.type ~= "Dependents" or not self.tooltipUI then return end
            if self:isMouseOver() and self.tooltip and self.tooltip ~= "" then
                if not self.tooltipUI:getIsVisible() then
                    self.tooltipUI:addToUIManager()
                    self.tooltipUI:setVisible(true)
                end
                self.tooltipUI.description = self.tooltip
            elseif self.tooltipUI:getIsVisible() then
                self.tooltipUI:setVisible(false)
                self.tooltipUI:removeFromUIManager()
            end
        end,

        setModInfo = function(orig, self, modInfo)
            if self.type ~= "Dependents" then return orig(self, modInfo) end

            self.modInfo   = modInfo
            self.modDict   = {}
            self.tooltip   = nil
            self.truncated = false

            local model = self.parent and self.parent.parent and self.parent.parent.model
            local mods  = model and model.mods
            local tm    = getTextManager()
            for _, id in ipairs(ZBetterModList.getDependentMods(modInfo)) do
                local data      = mods and mods[id]
                local available = data ~= nil
                local color     = DEFAULT_COLOR
                if data and data.isActive then color = ghc() end
                table.insert(self.modDict, {
                    id        = id,
                    color     = color,
                    len       = tm:MeasureStringX(UIFont.Small, id),
                    available = available,
                    modInfo   = data and data.modInfo or nil,
                })
            end

            local commaWidth = tm:MeasureStringX(UIFont.Small, ", ")
            local ellipsisW  = tm:MeasureStringX(UIFont.Small, " ...")
            local avail      = self.width - self.borderX - self.padX - I.UI_BORDER_SPACING

            local total = 0
            for i, v in ipairs(self.modDict) do
                total = total + v.len + (i > 1 and commaWidth or 0)
            end

            if total > avail then
                local full = {}
                for _, v in ipairs(self.modDict) do full[#full + 1] = v.id end
                self.tooltip = table.concat(full, ", ")

                local kept, used = {}, 0
                for _, v in ipairs(self.modDict) do
                    local need = v.len + (#kept > 0 and commaWidth or 0)
                    if used + need + ellipsisW > avail then break end
                    kept[#kept + 1] = v
                    used = used + need
                end
                self.modDict   = kept
                self.truncated = true
            end

            self:setHeight(I.BUTTON_HGT)
        end,

        render = function(orig, self, ...)
            if self.type ~= "Dependents" then return orig(self, ...) end

            self:drawRectBorder(0, 0, self.borderX, self.height,
                self.borderColor.a, self.borderColor.r, self.borderColor.g, self.borderColor.b)
            self:drawText(self.name,
                self.borderX - I.UI_BORDER_SPACING - self.labelWidth,
                (I.BUTTON_HGT - I.FONT_HGT_SMALL) / 2,
                0.9, 0.9, 0.9, 0.9, UIFont.Small)

            local tm         = getTextManager()
            local commaWidth = tm:MeasureStringX(UIFont.Small, ", ")
            local x          = self.borderX + self.padX
            local y          = 2

            for i, val in ipairs(self.modDict) do
                if i > 1 then
                    self:drawText(", ", x, y, 0.6, 0.6, 0.6, 0.9, UIFont.Small)
                    x = x + commaWidth
                end
                self:drawText(val.id, x, y, val.color.r, val.color.g, val.color.b, 0.9, UIFont.Small)

                local hovered = self:isMouseOver()
                    and self:getMouseX() > x and self:getMouseX() < x + val.len
                    and self:getMouseY() > y and self:getMouseY() < y + I.FONT_HGT_SMALL + 1
                if not hovered then
                    self:drawRectBorder(x, y + I.FONT_HGT_SMALL, val.len, 1, 0.9,
                        val.color.r, val.color.g, val.color.b)
                elseif self.pressed and val.available and val.modInfo then
                    self.parent:updateView(val.modInfo)
                end
                x = x + val.len
            end

            if self.truncated then
                self:drawText(" ...", x, y, 0.9, 0.9, 0.9, 0.9, UIFont.Small)
            end

            self.pressed = false
        end,
    },
    [ModInfoPanel.Param] = {
        render = function(orig, self, ...)
            if self.type == "WorkshopID" and self.workshopID == "" then
                self.workshopID = I.getWorkshopID(self.modInfo) or ""
            end

            orig(self, ...)

            local renderer = paramRenderers[self.type]
            if renderer and self.modInfo then
                renderer(self, paramCursor(self))
            end
        end,
    },
    [ModInfoPanel.Title] = {
        createChildren = function(orig, self)
            orig(self)

            local workshopBtnW = getTextManager():MeasureStringX(UIFont.Small, getText("UI_modselector_openWorkshopPage")) + I.UI_BORDER_SPACING * 2
            local unsubLabel = "Unsubscribe"
            local unsubBtnW = getTextManager():MeasureStringX(UIFont.Small, unsubLabel) + I.UI_BORDER_SPACING * 2
            local backBtnW = I.BUTTON_HGT
            local backBtnX = self.width - workshopBtnW - I.UI_BORDER_SPACING - unsubBtnW - I.UI_BORDER_SPACING - backBtnW - I.UI_BORDER_SPACING

            self.navBackBtn = ISButton:new(backBtnX, I.UI_BORDER_SPACING + 1, backBtnW, I.BUTTON_HGT, "<", self, onTitleNavBackBtn)
            I.styleTopBarButton(self.navBackBtn, getText("UI_modselector_navBackTooltip"))
            self.navBackBtn:setVisible(false)
            self:addChild(self.navBackBtn)

            self.unsubscribeBtn = ISButton:new(self.width - workshopBtnW - I.UI_BORDER_SPACING - unsubBtnW - I.UI_BORDER_SPACING, I.UI_BORDER_SPACING + 1, unsubBtnW, I.BUTTON_HGT, unsubLabel, self, onUnsubscribeBtn)
            self.unsubscribeBtn:initialise()
            self.unsubscribeBtn:instantiate()
            self.unsubscribeBtn:enableCancelColor()
            self:addChild(self.unsubscribeBtn)

            self.workshopBtn = ISButton:new(self.width - workshopBtnW - I.UI_BORDER_SPACING, I.UI_BORDER_SPACING + 1, workshopBtnW, I.BUTTON_HGT, getText("UI_modselector_openWorkshopPage"), self, function(titleSelf) onWorkshopBtn(titleSelf.workshopID) end)
            self.workshopBtn:initialise()
            self.workshopBtn:instantiate()
            self:addChild(self.workshopBtn)
        end,

        setModInfo = function(orig, self, modInfo)
            orig(self, modInfo)

            self.modInfo = modInfo
            local workshopID = I.getWorkshopID(modInfo)
            self.workshopID = workshopID
            local visible = workshopID ~= nil

            self.workshopBtn:setVisible(visible)

            if self.unsubscribeBtn then
                self.unsubscribeBtn:setVisible(visible)
                local canUnsubscribe = visible and not isAnyModInPackageEnabled(workshopID, modInfo)
                self.unsubscribeBtn:setEnable(canUnsubscribe)
            end

            if self.navBackBtn then
                local canBack = #I.navHistory > 0
                self.navBackBtn:setVisible(canBack)
                self.navBackBtn:setEnable(canBack)
            end
        end,
    }
})
