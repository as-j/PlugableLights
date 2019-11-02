definition(
    name: "Plugable Lights: Max on time",
    namespace: "asj",
    author: "asj",
    parent: "asj:Plugable Lights Motion triggered",
    description: "Turn off the light after max time, even if the contact is open",
    iconUrl: "",
    iconX2Url: "") {

    preferences {
        page(name: "mainPage", title: "Settings Page", install: true, uninstall: true) {
            section("App Name") {
                label title: "App Name", defaultValue: app.label, required: true
            }
            section() {
                def parent_settings = parent?.getSettings()
                paragraph "Default Off Time: ${parent_settings?.timeOffS ?: "No Off time, using 0"}"
            }
            section("Force Off Time") {
                input "timeForceOffMin", "number", title: "Turn off After Minutes even if open:", require: true
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
    parent.pluginRemoved()
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
    def callbacks = [
        label: app.label,
        functions: [ "postTurnOn", "turnOffEvent" ]
    ]
    return callbacks
}

def postTurnOn(values) {
    if (logEnable) log.debug "${app.label} postTurnOn(): state: ${state}"

    unschedule("forceOffTimeout")
    runIn(timeForceOffMin * 60, forceOffTimeout)

    return false
}

def turnOffEvent(values) {
    if (logEnable) log.debug "${app.label} turnOffEvent: ${state}"

    unschedule("forceOffTimeout")

    return false

}

def forceOffTimeout() {
    parent.turnOff()
}
