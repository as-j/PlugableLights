definition(
    name: "Plugable Lights: Short Off Plugin",
    namespace: "asj",
    author: "asj",
    parent: "asj:Plugable Lights Motion triggered",
    description: "Longer period of motion increases the stay on time after motion ends",
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
            section("Extend off times by") {
                input "timeOffTimeS", "number", title: "If time is less than seconds, extend on time:", require: true
                input "timeExtendS", "number", title: "Amount of seconds to extend off by:", require: true
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
        functions: [ "postTurnOn", "postTurnOffEvent", "postTurnOff" ]
    ]
    return callbacks
}

def postTurnOn(values) {
    if (logEnable) log.debug "${app.label} postTurnOn(): extTimeOff: ${state}"

    state.turnOnAt = now()
    if (!state?.turnOffAt) return

    long delta = (state.turnOnAt - state.turnOffAt)/1000
    if (delta > timeOffTimeS) {
        state.extTimeOffS = 0
    } else {
        state.extTimeOffS = timeExtendS
    }

    if (logEnable) log.debug "postTurnOn(): new extTimeOff: ${state?.extTimeOffS} delta: ${delta}"
    return false
}

def postTurnOffEvent(values) {
    if (logEnable) log.debug "postTurnOffEvent(): extTimeOff: ${state.extTimeOffS} initial: ${values.timeOffS}"
    if (!state?.turnOnAt) return false
    if (!state?.turnOffAt) return false

    values.timeOffS += (state.extTimeOffS ?: 0)*1000
    state.extTimeOffS = 0

    if (logEnable) log.debug "extTimeOffCalc(): new extTimeOff: ${values.timeOffS}"

    return false

}

def postTurnOff(values) {
    if (logEnable) log.debug "postTurnOff(): extTimeOff: ${state.extTimeOffS} initial: ${values.timeOffS}"
    if (!state?.turnOnAt) return false

    state.turnOffAt = now()

    return false
}


def extOffReset() {
    state.extTimeOffS = parentTimeOffS()
}

def parentTimeOffS() {
    return parent.settings?.timeOffS ?: 0
}

