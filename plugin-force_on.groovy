definition(
    name: "Plugable Lights: Force On Plugin",
    namespace: "asj",
    author: "asj",
    parent: "asj:Plugable Lights Motion triggered",
    description: "Ability to stop devices from turning on due to a switch being on",
    iconUrl: "",
    iconX2Url: "") {

    preferences {
        page(name: "mainPage", title: "Settings Page", install: true, uninstall: true) {
            section() {
                label title: "App Name", defaultValue: app.label, required: true
            }
            section("Devices to force on") {
                def parent_settings = parent?.getSettings()
                parent_settings?.switch_default?.each { device ->
                    paragraph "Device: $device"
                }
                input name: "turnOnWithSwitch", type: "capability.switch", title: "Turn on lights when switch on", multiple: false, required: true
                input name: "turnOffWhenSwitchOff", type: "bool", title: "Turn Off Device(s) When Switch Turned Off", defaultValue: true
                input name: "restoreTimeS", type: "number", title: "After Switch Off Time Re-enable motion", defaultValue: 15
            }
            section("Debug Settings") {
                //standard logging options
                input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
                input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: false
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

    settings.turnOnWithSwitch.each { device ->
        subscribe(device, "switch.on", switchOnEvent)
    }

    settings.turnOnWithSwitch.each { device ->
        subscribe(device, "switch.off", switchOffEvent)
    }

    if (logEnable) log.debug "${app.label}: refresh done! turnOnWithSwitch: ${settings.turnOnWithSwitch}"
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
        functions: [ "parentUpdated" ]
    ]
    return callbacks
}

def parentUpdated(values) {
    updated()
}

def switchOnEvent(evt) {
    if (logEnable) log.debug "switchOnEvent(): $evt.displayName($evt.name) $evt.value"

    parent.clearSubscriptions()
    parent.clearScheduledEvents()
    parent.turnOnEvent([displayName: "turnOnEvent",
                   name: "turnOnEvent",
                   value: "on"])
}
def switchOffEvent(evt) {
    if (logEnable) log.debug "switchOffEvent(): $evt.displayName($evt.name) $evt.value"

    if (settings.turnOffWhenSwitchOff) {
        parent.turnOff()
    }
    runIn(settings.restoreTimeS ?: 0, restoreMotion)
}

def restoreMotion() {
    parent.setupSubscriptions()
}

