definition(
    name: "Plugable Lights: Delay Off Plugin",
    namespace: "asj",
    author: "asj",
    parent: "asj:Plugable Lights Motion triggered",
    description: "Longer period of motion increases the stay on time after motion ends",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png") {

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
                input "timeMaxOffS", "number", title: "Maximum Off Time (s)", require: true
                input "timeResetIdleS", "number", title: "Reset Off time when Inactive For (s)", require: true
                input "timeOnScaling", "decimal", title: "Scale factor, how much more important active is vs inactive", defaultValue: 2.0, require: true
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
    if (logEnable) log.debug "${app.label} postTurnOn(): extTimeOff: ${state}"

    state.turnOnAt = now()
    unschedule("extOffReset")

    if (!state?.turnOffAt) return

    long delta = (state.turnOnAt - state.turnOffAt)/1000
    if (delta < 0) delta = 0

    def timeOffS = parentTimeOffS()
    if(!state?.extTimeOffS) state.extTimeOffS = timeOffS

    state.extTimeOffS -= delta
    if (state.extTimeOffS < timeOffS) {
        state.extTimeOffS = timeOffS
    }

    if (logEnable) log.debug "extTimeOnCalc(): new extTimeOff: ${state?.extTimeOffS} delta: ${delta}"
    return false
}

def turnOffEvent(values) {
    if (logEnable) log.debug "extTimeOffCalc(): extTimeOff: ${state.extTimeOffS}"
    if (!state?.turnOnAt) return false

    state.turnOffAt = now()

    long delta = (state.turnOffAt - state.turnOnAt)/1000
    if (delta < 0) delta = 0

    if (!state?.extTimeOffS) state.extTimeOffS = (long) parentTimeOffS()

    state.extTimeOffS += delta * settings.timeOnScaling
    if (state.extTimeOffS > settings.timeMaxOffS) state.extTimeOffS = settings.timeMaxOffS

    values.timeOffS = (long) state.extTimeOffS

    def date = new Date()
    long resetTime = now() + ((state.extTimeOffS + settings.timeResetIdleS)*1000)
    date.setTime(resetTime)
    schedule(date, "extOffReset")

    if (logEnable) log.debug "extTimeOffCalc(): new extTimeOff: ${state.extTimeOffS} delta: ${delta}"

    return false

}

def extOffReset() {
    state.extTimeOffS = parentTimeOffS()
}

def parentTimeOffS() {
    return parent.settings?.timeOffS ?: 0
}
