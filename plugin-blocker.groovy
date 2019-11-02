definition(
    name: "Plugable Lights: Bocking Device Plugin",
    namespace: "asj",
    author: "asj",
    parent: "asj:Plugable Lights Motion triggered",
    description: "Ability to stop devices from turning on due to a switch being on",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png") {

    preferences {
        page(name: "mainPage", title: "Settings Page", install: true, uninstall: true) {
            section() {
                label title: "App Name", defaultValue: app.label, required: true
                input name: "switch_block", type: "capability.switch", title: "When Switch(s) are on Block Motion Sensors", multiple: true, required: false
            }
            section("Devices to turn off/on on block enable/disable") {
                def parent_settings = parent?.getSettings()
                parent_settings?.switch_default?.each { device ->
                    paragraph "Device: $device"
                }
                input name: "turnOffWhenBlockEnabled", type: "bool", title: "Turn off lights when block enabled", defaultValue: true
                input name: "turnOnWhenBlockDisabled", type: "bool", title: "Turn on ligts when block disabled", defaultValue: true
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

    settings.switch_block.each { device ->
        subscribe(device, "switch.on", blockOnEvent)
    }

    settings.switch_block.each { device ->
        subscribe(device, "switch.off", blockOffEvent)
    }

    if (logEnable) log.debug "${app.label}: refresh done! swtich_block: ${settings.switch_block}"
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

def blockOnEvent(evt) {
    if (logEnable) log.debug "turnOnEvent(): $evt.displayName($evt.name) $evt.value"

    parent.unsubscribe("turnOnEvent")
    parent.unsubscribe("turnOffEvent")
    parent.unschedule("turnOff")
    if (settings.turnOffWhenBlockEnabled) {
        def parent_settings = parent?.getSettings()
            parent_settings?.switch_default?.each { device ->
                device.off()
        }
        parent.turnOff()
    }
}
def blockOffEvent(evt) {
    if (logEnable) log.debug "turnOnEvent(): $evt.displayName($evt.name) $evt.value"
    parent.updated()

    if (settings.turnOnWhenBlockDisabled) {
        def parent_settings = parent?.getSettings()
        boolean active = false
        parent_settings?.turnOnMotionSensor?.each { device ->
            if (device.currentValue('motion') == 'active') active = true
        }
        parent_settings?.turnOnContactSensor?.each { device ->
            if (device.currentValue('contact') == 'open') active = true
        }
        parent.turnOnEvent displayName: "blockerOffEvent", name: "blockerOffEvent", value: "on"
    }
}
