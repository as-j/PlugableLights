definition(
    name: "Plugable Lights: Mode Plugin",
    namespace: "asj",
    author: "asj",
    parent: "asj:Plugable Lights Motion triggered",
    description: "Set Different Levels Per Mode",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png") {

    preferences {
        page(name: "mainPage", title: "Settings Page", install: true, uninstall: true) {
            section("App Name") {
                label title: "App Name", defaultValue: app.label, required: true
            }
            section() {
                def parent_settings = parent?.getSettings()
                paragraph "Default Level: ${parent_settings?.level_default ?: "No default"}"
                paragraph "Default Color Temperature: ${parent_settings?.temp_default ?: "No Default"}"
                paragraph "Default On Devices: ${parent_settings?.switch_default ?: "No Default On Devices"}"
                paragraph "Default Off Devices: ${parent_settings?.switch_off_default ?: "No Default Off Devices"}"
            }
            section() {
                input "override_modes", "mode", title: "Modes to override defaults", require: false, multiple: true, submitOnChange: true
            }
            settings?.override_modes.each { mode ->
                section("Mode: $mode", hideable: true) {
                    input "level_$mode", "number", title: "Level: $mode", require: false
                    input "temp_$mode", "number", title: "Color Temp: $mode", require: false
                    input "switch_$mode", "capability.switch", title: "Devices To Turn On for $mode", multiple: true, required: false
                    input "switch_off_$mode", "capability.switch", title: "Devices To Turn Off for $mode", multiple: true, required: false
                }
            }
            section("Debug Settings") {
                //standard logging options
                input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false, submitOnChange: true
                input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: false, submitOnChange: true
            }
        }
    }
}


/**
 *  installed()
 *
 *  Runs when the app is first installed.
 **/
def installed() {
    state.installedAt = now()
    if (logEnable) log.debug "${app.label}: Installed with settings: ${settings}" 
    updated()
}

/**
 *  uninstalled()
 *
 *  Runs when the app is uninstalled.
 **/
def uninstalled() {
    if (logEnable) log.debug "${app.label}: Uninstalled"
    parent.pluginUninstalled(app.label)
}

/**
 *  updated()
 * 
 *  Runs when app settings are changed.
 * 
 *  Updates device.state with input values and other hard-coded values.
 *  Refreshes scheduling and subscriptions.
 **/
def updated() {
    sendFuncsToParent()
    if (logEnable) log.debug "${app.label}: refresh done!"
}

def sendFuncsToParent() {
    if (!app.label) {
        runIn(5, sendFuncsToParent)
        return
    }
    if (logEnable) log.debug "${app.label}: sendFuncsToParent(): calling pluginRefresh"
    parent.pluginRefresh()
}

def getPluginFunctions() {
    return [
        label: app.label,
        functions: [ "preTurnOn", "preTurnOff", "parentUpdated" ]
    ]
}

def testFunc() {
    if (logEnable) log.debug "${app.label}: preTurnOff(): updated ${settings}"
}

def parentUpdated(parent_settings) {
    if (logEnable) log.debug "${app.label}: parent_settings? ${parent_settings}"
    return false
}

def preTurnOn(values) {
    if (logEnable) log.debug "${app.label}: preTurnOn(): updated ${values}"

    def mode = location.currentMode
    state.turnOnMode = mode.name

    if ((settings."switch_$mode") || (settings."switch_off_$mode")) {
        values.devices = settings."switch_$mode"
        values.devices_off = settings."switch_off_$mode"
    }
    if (settings."level_$mode") values.mode_level = settings."level_$mode"
    if (settings."temp_$mode") values.mode_temp = settings."temp_$mode"
    return false
}

def preTurnOff(values) {
    if (logEnable) log.debug "${app.label}: preTurnOff(): updated ${values}"
    def mode = state.turnOnMode

    if (settings."switch_$mode") values.devices = settings."switch_$mode"
    return false
}


