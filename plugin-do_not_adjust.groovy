definition(
    name: "Plugable Lights: Do not adjust Plugin",
    namespace: "asj",
    author: "asj",
    parent: "asj:Plugable Lights Motion triggered",
    description: "Once lights are turned on, don't reset on/off or level until off",
    iconUrl: "",
    iconX2Url: "") {

    preferences {
        page(name: "mainPage", title: "Settings Page", install: true, uninstall: true) {
            section() {
                input "thisName", "text", title: "Name of this Application", defaultValue: "Do not adjust", submitOnChange: true
                if(thisName) app.updateLabel(thisName)             
            }
            section("Debug Settings") {
                //standard logging options
                input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false, submitOnChange: true
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
    if (logEnable) log.debug "${app.label}: updated!"
    if (logEnable) runIn(1800, "logsOff")
}

def logsOff(){
    log.debug "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
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
        functions: [ "preTurnOn", "postTurnOff" ]
    ]
    return callbacks
}

def parentUpdated(values) {
    updated()
}

def preTurnOn(values) {
    if (logEnable) log.debug "${app.label}: preTurnOn(): updated ${values} already on: ${state.alreadyOn}"

    if (state.alreadyOn) return "skip: do not adjust, already on"
    
    state.alreadyOn = true
    return false
    
}

def postTurnOff(values) {
    if (logEnable) log.debug "${app.label}: preTurnOff(): updated ${values} already on: ${state.alreadyOn}"

    state.alreadyOn = false
    return false
}

