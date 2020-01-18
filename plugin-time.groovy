definition(
    name: "Plugable Lights: Time Plugin",
    namespace: "asj",
    author: "asj",
    parent: "asj:Plugable Lights Motion triggered",
    description: "Set Different Levels Per Different Time",
    iconUrl: "",
    iconX2Url: "") {

    preferences {
        page(name: "mainPage", title: "Settings Page", install: true, uninstall: true) {
            section("App Name") {
                label title: "App Name", defaultValue: "Time Plugin", required: true, submitOnChange: true
            }
            section() {
                def parent_settings = parent?.getSettings()
                paragraph "Default Level: ${parent_settings?.level_default ?: "No default"}"
                paragraph "Default Color Temperature: ${parent_settings?.temp_default ?: "No Default"}"
                paragraph "Default On Devices: ${parent_settings?.switch_default ?: "No Default On Devices"}"
                paragraph "Default Off Devices: ${parent_settings?.switch_off_default ?: "No Default Off Devices"}"
            }
            section() {
                input name: "turnOnTime",  type: "time", title: "Start of Interval Time", required: true, submitOnChange: true
                input "level", "number", title: "Turn On Level", require: false
                input "temp", "number", title: "Turn On Color Temp", require: false
                input "switch", "capability.switch", title: "Devices To Turn On", multiple: true, required: false
                input "switch_off", "capability.switch", title: "Devices To Turn Off", multiple: true, required: false
                input name: "turnOffTime",  type: "time", title: "End of Interval Time", required: true, submitOnChange: true
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
    parent.updated()
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

def isTimeBetween() {
    if (!settings.turnOnTime) return false
    if (!settings.turnOffTime) return false
    
    def onDate = toDateTime(settings.turnOnTime)
    def onHour = onDate.format("HH").toInteger()
    def onMinute = onDate.format("mm").toInteger()
    def onDec = onHour*100 + onMinute

    def offDate = toDateTime(settings.turnOffTime)
    def offHour = offDate.format("HH").toInteger()
    def offMinute = offDate.format("mm").toInteger()
    def offDec = offHour*100 + offMinute
    
    def currently = new Date()
    def nowHour = currently.format("HH").toInteger()
    def nowMinute = currently.format("mm").toInteger()
    def nowDec = nowHour*100 + nowMinute
    
    if (logEnable) log.debug "isBetween(): on: $onDec off: $offDec now: $nowDec"
    
    if (onDec <= offDec) { // Ex: 0900 -> 2100 
        if ((nowDec >= onDec) && (nowDec < offDec)) return true
    } else { // Ex: 2100 -> 0730
        if ((nowDec >= onDec) || (nowDec < offDec)) return true
    }
    return false
}


def preTurnOn(values) {
    if (logEnable) log.debug "${app.label}: preTurnOn(): updated ${values}"
    
    if (!isTimeBetween()) {
        state.turnOnInTime = false
        return false
    }

    state.turnOnInTime = true

    if ((settings.switch) || (settings.switch_off)) {
        values.devices = settings.switch
        values.devices_off = settings.switch_off
    }
    if (settings.level) values.mode_level = settings.level
    if (settings.temp) values.mode_temp = settings.temp
    
    if (logEnable) log.debug "${app.label}: preTurnOn(): new values ${values}"
    return false
}

def preTurnOff(values) {
    if (logEnable) log.debug "${app.label}: preTurnOff(): updated ${values}"
    
    if (!state.turnOnInMode) return false
    
    if (settings.switch) values.devices = settings.switch
    if (settings.switch_off) values.devices += settings.switch_off
    
    if (logEnable) log.debug "${app.label}: preTurnOff(): mode $mode values: ${values}"
    
    return false
}




