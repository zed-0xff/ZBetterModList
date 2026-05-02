--[[ ModListPanel + ModListBox zdk hooks (skipped when ModManager is active). ]]

local I = ZBetterModList._I

if not I.hasModManager then
    zdk.hook({
        [ModSelector.ModListPanel] = {
            createChildren = function(orig, self)
                I.updateLists()

                orig(self)

                if not self.enabledModsTickbox or not self.filterPanel then return end

                self.enabledModsTickbox:setVisible(false)

                local label = ISLabel:new(I.UI_BORDER_SPACING+1, self.filterCombo:getBottom() + I.UI_BORDER_SPACING, I.LABEL_HGT, getText("UI_modselector_show"), 1.0, 1.0, 1.0, 1.0, UIFont.Medium, true)
                self.filterPanel:addChild(label)

                self.showCombo = ISComboBox:new(self.filterCombo:getX(), label.y, self.filterCombo:getWidth(), I.BUTTON_HGT, self, self.updateView)
                self.showCombo:initialise()

                for _, option in ipairs(I.SHOW_MOD_OPTIONS) do
                    self.showCombo:addOption(getText("UI_modselector_show" .. option))
                end

                self.showCombo.selected = 1
                self.filterPanel:addChild(self.showCombo)

                local sortLabel = ISLabel:new(self.showCombo:getRight() + I.UI_BORDER_SPACING * 2, label.y, I.LABEL_HGT, getText("UI_modselector_sortBy"), 1.0, 1.0, 1.0, 1.0, UIFont.Medium, true)
                self.filterPanel:addChild(sortLabel)

                local sortWidth = I.BUTTON_HGT + I.UI_BORDER_SPACING + getTextManager():MeasureStringX(UIFont.Small, getText("UI_modselector_sortByDate"))
                self.sortCombo = ISComboBox:new(sortLabel:getRight() + I.UI_BORDER_SPACING, sortLabel.y, sortWidth, I.BUTTON_HGT, self, self.updateView)
                self.sortCombo:initialise()

                for _, option in ipairs(I.SORT_OPTIONS) do
                    self.sortCombo:addOption(getText("UI_modselector_sortBy" .. option))
                end

                self.sortCombo.selected = I.readSortOrder()
                self.filterPanel:addChild(self.sortCombo)

                local collLabel = ISLabel:new(self.sortCombo:getRight() + I.UI_BORDER_SPACING * 2, label.y, I.LABEL_HGT, getText("UI_modselector_collection"), 1.0, 1.0, 1.0, 1.0, UIFont.Medium, true)
                self.filterPanel:addChild(collLabel)

                local collWidth = getCore():getScreenWidth() / 10
                self.collectionCombo = ISComboBox:new(collLabel:getRight() + I.UI_BORDER_SPACING, label.y, collWidth, I.BUTTON_HGT, self, I.onCollectionComboChange)
                self.collectionCombo:initialise()
                self.filterPanel:addChild(self.collectionCombo)

                local btnW = I.BUTTON_HGT
                self.addCollectionBtn = ISButton:new(self.collectionCombo:getRight() + I.UI_BORDER_SPACING, label.y, btnW, I.BUTTON_HGT, "+", self, I.onAddCollectionBtn)
                self.addCollectionBtn:initialise()
                self.addCollectionBtn.tooltip = getText("UI_modselector_collection_addTooltip")
                self.filterPanel:addChild(self.addCollectionBtn)

                self.removeCollectionBtn = ISButton:new(self.addCollectionBtn:getRight() + I.UI_BORDER_SPACING, label.y, btnW, I.BUTTON_HGT, "-", self, I.onRemoveCollectionBtn)
                self.removeCollectionBtn:initialise()
                self.removeCollectionBtn.tooltip = getText("UI_modselector_collection_removeTooltip")
                self.filterPanel:addChild(self.removeCollectionBtn)

                self.refreshCollectionBtn = ISButton:new(self.removeCollectionBtn:getRight() + I.UI_BORDER_SPACING, label.y, btnW, I.BUTTON_HGT, "", self, I.onRefreshCollectionBtn)
                self.refreshCollectionBtn:initialise()
                self.refreshCollectionBtn:setImage(getTexture("media/textures/ZBetterModList/refresh.png"))
                self.refreshCollectionBtn.tooltip = getText("UI_modselector_collection_refreshTooltip")
                self.filterPanel:addChild(self.refreshCollectionBtn)

                I.rebuildCollectionCombo(self)
            end,

            prerender = function(orig, self)
                orig(self)
                if I.Collections.poll() then
                    I.rebuildCollectionCombo(self)
                    if self.selectedCollectionId then self:updateView() end
                end
                if I.Collections.pollInstalls() and ModSelector.instance then
                    if SteamLuaHelper and SteamLuaHelper.resetModFolders then
                        SteamLuaHelper.resetModFolders()
                    end
                    I.updateLists()
                    ModSelector.instance:reloadMods()
                end
            end,

            applyFilters = function(orig, self)
                orig(self)

                if not self.showCombo then return end

                I.queryWorkshopDetails(self)

                local showMode = I.SHOW_MOD_OPTIONS[self.showCombo.selected]
                if showMode == "All" then
                    if ZBetterModList.known_mods_after == nil then
                        ZBetterModList.known_mods_after = {}
                        for _, modData in pairs(self.model.currentMods) do
                            ZBetterModList.known_mods_after[modData.modInfo:getId()] = true
                        end
                        I.writeKnownList(ZBetterModList.known_mods_after)
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
                            if I.isJavaMod(modData.modInfo) then
                                table.insert(newTbl, modData)
                            end
                        elseif showMode == "New" then
                            if not ZBetterModList.known_mods_before[modData.modInfo:getId()] then
                                table.insert(newTbl, modData)
                            end
                        elseif showMode == "B41" then
                            if I.isB41Mod(modData.modInfo) then
                                table.insert(newTbl, modData)
                            end
                        end
                    end
                    self.model.currentMods = newTbl
                end

                if self.selectedCollectionId then
                    local filtered = {}
                    for _, modData in pairs(self.model.currentMods) do
                        local wsId = I.getWorkshopID(modData.modInfo)
                        if wsId and I.Collections.contains(self.selectedCollectionId, wsId) then
                            table.insert(filtered, modData)
                        end
                    end
                    self.model.currentMods = filtered
                end

                I.writeSortOrder(self.sortCombo.selected)
                local sortMode = I.SORT_OPTIONS[self.sortCombo.selected]
                if sortMode == "Date" then
                    table.sort(self.model.currentMods, function(a, b)
                        return I.getModTimeUpdated(a.modInfo) > I.getModTimeUpdated(b.modInfo)
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

                local timeUpdated = I.getModTimeUpdated(item.item.modInfo)
                if timeUpdated > 0 then
                    local ageText = I.formatAge(timeUpdated)
                    if ageText ~= "" then
                        local ageWidth = getTextManager():MeasureStringX(UIFont.Small, ageText)
                        local starX = self:getWidth() - I.UI_BORDER_SPACING - I.BUTTON_HGT - 1
                        local height = nextY - y
                        local itemPadY = (height - I.FONT_HGT_SMALL) / 2
                        local diff = os.time() - timeUpdated
                        local r, g, b = 0.6, 0.6, 0.6
                        if diff < 86400 * 2 then
                            r, g, b = 0.4, 0.85, 0.4
                        elseif diff > 86400 * 180 then
                            r, g, b = 0.7, 0.5, 0.3
                        end
                        self:drawText(ageText, starX - ageWidth - I.UI_BORDER_SPACING * 2, y + itemPadY, r, g, b, 0.7, UIFont.Small)
                    end
                end

                return nextY
            end,
        },
    })
end
