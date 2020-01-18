definition(
    name: "Plugable Lights: Turn On of Off At A Set Time",
    namespace: "asj",
    author: "asj",
    parent: "asj:Plugable Lights Motion triggered",
    description: "Turn off a light at a certain time, even when there's no motion and stop forcing it on after a time",
    iconUrl: "",
    iconX2Url: "") {

    preferences {
        page(name: "mainPage", title: "Settings Page", install: true, uninstall: true) {
            section() {
                paragraph("Turn on a light at a certain time, even if there's no motion detected." +
                          "Then at a later time, return back to motion detection again." +
                          "For example turn on an outdoor light from 8pm to 10pm.")
            }
            section() {
                label title: "App Name", defaultValue: app.getName(), required: true
            }
            section("Devices to turn off/on on block enable/disable") {
                input name: "turnOnTime",  type: "time", title: "Turn on at", required: true, submitOnChange: true
                input name: "turnOffTime",  type: "time", title: "Turn off at", required: true, submitOnChange: true
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
    parent.pluginRemoved(app.label)
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
    
    def onDate = toDateTime(settings.turnOnTime)
    def onHour = onDate.format("HH")
    def onMinute = onDate.format("mm")

    schedule("2 $onMinute $onHour * * ?", onEvent)


    def offDate = toDateTime(settings.turnOffTime)
    def offHour = offDate.format("HH")
    def offMinute = offDate.format("mm")

    schedule("2 $offMinute $offHour * * ?", offEvent)

    if (logEnable) log.debug "${app.label}: refresh done! from $onHour:$onMinute -> $offHour:$offMinute"
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

def offEvent() {
    if (logEnable) log.debug "offEvent(): time to turn off"

    parent.setupSubscriptions()

    def parent_settings = parent?.getSettings()
    boolean active = false
    parent_settings?.turnOnMotionSensor?.each { device ->
        if (device.currentValue('motion') == 'active') active = true
    }
    parent_settings?.turnOnContactSensor?.each { device ->
        if (device.currentValue('contact') == 'open') active = true
    }
    
    if (!active) parent.turnOffEvent displayName: "timedOffEvent", name: "timedOffEvent", value: "off"
}
def onEvent() {
    if (logEnable) log.debug "onEvent(): time to turn on!"
    
    parent.clearSubscriptions()
    parent.clearScheduledEvents()

    parent.turnOnEvent displayName: "timedOnEvent", name: "timedOnEvent", value: "on"
    
}


