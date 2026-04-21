--[[
  ZBetterModList — entry point. Loads workshop collections support, then core
  logic and UI hook modules. Split layout:

    ZBetterModList_Core.lua          — state, helpers, navigation, bulk toggle
    ZBetterModList_HooksModList.lua  — ModListPanel / ModListBox (no ModManager)
    ZBetterModList_HooksInfoPanel.lua — ModInfoPanel / Title / Dependents row
    ZBetterModList_HooksModSelector.lua — ModSelector tabs + enable/disable all

  Shared bindings for hooks live in ZBetterModList._I (set by Core).
]]

require "OptionScreens/ModSelector/ModListPanel"
require "OptionScreens/ModSelector/ModListBox"
require "ZBetterModList_Collections"
require "ZBetterModList_Core"
require "ZBetterModList_HooksModList"
require "ZBetterModList_HooksInfoPanel"
require "ZBetterModList_HooksModSelector"
