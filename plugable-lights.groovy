import groovy.transform.Field

@Field static List plugIns = [
    [
        appName: "Plugable Lights: Mode Plugin",
        name: "plugInMode",
        title: "Mode Plugin",
    ],
    [
        appName: "Plugable Lights: Delay Off Plugin",
        name: "plugInDelayOff",
        title: "Delayed Off Plugin",
    ],
    [
        appName: "Plugable Lights: Bocking Device Plugin",
        name: "plugInBlock",
        title: "Block Plugin",
    ],
    [
        appName: "Plugable Lights: Max on time",
        name: "plugInMaxOn",
        title: "Force Off After Max Time",
    ],
    [
        appName: "Plugable Lights: Short Off Plugin",
        name: "plugInShortOff",
        title: "Extend on time if there's a short off",
    ],
    [
        appName: "Plugable Lights: Force On Plugin",
        name: "plugInForceOn",
        title: "Force Lights On and Disable Motion Detection with Switch",
    ],
]

// getChildApps seemms to be slow, ~40ms so cache child app
@Field static Map childCache = [:]

definition(
    name: "Plugable Lights Motion triggered",
    namespace: "asj",
    author: "asj",
    description: "Motion or Contact triggered light with plugable child apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png") {

    preferences {
        page(name: "mainPage", title: "Settings Page", install: true, uninstall: true) {
            section("App Name") {
                label title: "App Name", defaultValue: app.label, required: true
            }
            section("Turn On Trigger") {
                input "turnOnMotionSensor", "capability.motionSensor", title: "Motion Sensor Becomes Active", multiple: true, required: false
                input "turnOnContactSensor", "capability.contactSensor", title: "Contacts Open", multiple: true, required: false
            }
            section("Turn Off Triggers") {
                input "timeOffS", "number", title: "Turn off after motion ends or contact closes (s)", defaultValue: 120
                input "turnOffMotionSensor", "capability.motionSensor", title: "Motion Sensor Becomes Inactive", multiple: true
                input "turnOffContactSensor", "capability.contactSensor", title: "Contacts Close", multiple: true
            }
            section("Defaults for all modes") {
                input "level_default", "number", title: "Default Level", require: false
                input "temp_default", "number", title: "Default Temp", require: false
                input "switch_default", "capability.switch", title: "Devices To Turn On", multiple: true, required: false
                input "switch_off_default", "capability.switch", title: "Devices To Turn Off", multiple: true, required: false
            }
            section("Plugins") {
                plugIns.each { 
                    app name: it.name, appName: it.appName, namespace: it?.namespace ?: "asj", title: it.title
                }
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
    state.callbacks = [:]
    state.stats = [:]
    if (logEnable) log.debug "${app.label}: Installed with settings: ${settings}" 
    updated()
}

/**
 *  uninstalled()
 *
 *  Runs when the app is uninstalled.
 **/
def uninstalled() {
    unschedule()
    unsubscribe()
    if (logEnable) log.debug "${app.label}: Uninstalled"
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
    if (logEnable) log.debug "${app.label}: updated ${settings}"

    unsubscribe()

    // Turn on devices
    settings.turnOnMotionSensor.each { device ->
        subscribe(device, "motion.active", turnOnEvent)
    }
    settings.turnOnContactSensor.each { device ->
        subscribe(device, "contact.open", turnOnEvent)
    }

    // Turn off devices
    settings.turnOffMotionSensor.each { device ->
        subscribe(device, "motion.inactive", turnOffEvent)
    }
    settings.turnOffContactSensor.each { device ->
        subscribe(device, "contact.closed", turnOffEvent)
    }
    settings.switch_default.each { device ->
        if (logEnable) log.debug "turnOnEvent(): ${device.currentValue("level")}"
    }

    pluginExec("parentUpdated", settings)
}

def turnOnEvent(evt) {
    def start = now()
    def values = [
        devices: settings.switch_default,
        devices_off: settings.switch_off_default,
        mode_level: settings.level_default,
        mode_temp: settings.temp_default,
    ]

    if (logEnable) log.debug "turnOnEvent(): $evt.displayName($evt.name) $evt.value values: $values"

    def skip = pluginExec("preTurnOn", values)

    if (logEnable) log.debug "turnOnEvent(): skip: $skip new values: $values $skip"
    if (skip) return

    values.devices.each { device ->
        if (logEnable) log.debug "turnOnEvent(): turning on $device"

        skip = pluginExec("turnOn", device)
        if (skip) return

        if (values.mode_level && device.hasCommand("setLevel")) {
            if (logEnable) log.debug "turnOnEvent(): has setLevel ${device.currentValue('level')}"
            if (device.currentValue('level') != values.mode_level) device.setLevel(values.mode_level)
        }
        if (values.mode_temp && device.hasCommand("setColorTemperature")) {
            if (device.currentValue('colorTemperature') != mode_temp) device.setColorTemperature(values.mode_temp)
        }
        if (device.currentValue('switch') != "on") device.on()
    }

    values.devices_off.each { device ->
        if (logEnable) log.debug "turnOnEvent(): turning off $device"
        if (device.currentValue('switch') != "off") device.off()
    }

    def text ="${app.label} turn on event"
    if (txtEnable) log.info text
    sendEvent(name: "switch", value: "on", descriptionText: text)

    unschedule("turnOff")

    pluginExec("postTurnOn", values)
    def delta = now() - start
    log.debug "turnOnEvent(): time: $delta"
}

def turnOffEvent(evt) {
    if (logEnable) log.debug "turnOffEvent(): $evt.displayName($evt.name) $evt.value"

    def num_active = 0
    settings.turnOffMotionSensor.each { device ->
        if (logEnable) log.debug "turnOffEvent(): $device motion: ${device.currentValue('motion')}"
        if (device.currentValue('motion') != 'inactive') num_active++
    }
    if (num_active > 0) {
        if (logEnable) log.debug "turnOffEvent(): skipping off, still active sensors: $num_active"
        return
    }

    def values = [
        timeOffS: settings.timeOffS*1000,
    ]
    def skip = pluginExec("turnOffEvent", values)
    if (skip) return

    if (values.timeOffS) {
        unschedule("turnOff")

        def date = new Date()
        long timeOffS = now() + values.timeOffS
        date.setTime(timeOffS)
        schedule(date, "turnOff")

        def text ="${app.label} scheduled turn off event"
        if (txtEnable) log.info text
        sendEvent(name: "switch", value: "scheduled", descriptionText: text)
    } else {
        turnOff()
    }
    pluginExec("postTurnOffEvent", values)
}

def turnOff() {
    if (logEnable) log.debug "turnOff() ${settings.switch_default}"

    def values = [
        devices: settings.switch_default,
    ]

    def skip = pluginExec("preTurnOff", values)
    if (logEnable) log.debug "turnOff() skip: $skip new values ${values}"
    if (skip) return


    values.devices.each { device ->
        if (logEnable) log.debug "turnOff(): turning off $device"
        device.off()
    }

    def text ="${app.label} turn off event"
    if (txtEnable) log.info text

    sendEvent(name: "switch", value: "off", descriptionText: text)
    pluginExec("postTurnOff", values)
}

def pluginRefresh() {
    if (logEnable) log.debug "${app.label}: pluginRefresh()"
    refreshCallbacks()

}

def pluginRemoved(app_label) {
    state.callbacks.remove(app_label)
}

def refreshCallbacks() {
    def apps = getChildApps()
    state.callbacks = [:]
    apps.each { app ->
        def appInfo = app.getPluginFunctions()
        if (logEnable) log.debug "${app.label}: refreshCallbacks(): ${appInfo?.label} functions: ${appInfo?.functions}"
        if (appInfo.label) {
            state.callbacks[appInfo.label] = [
                functions: appInfo?.functions
            ]
            if (logEnable) log.debug "${app.label}: refreshCallbacks(): cache -> ${appInfo.label} -> ${app}"
            childCache[appInfo.label] = app
        }
    }
}

def pluginExec(func, arg) {
    if (logEnable) log.debug "${app.label}: pluginExec: num plugins: ${state.callbacks.size()}"
    if (!state.callbacks) {
        if (logEnable) log.debug "${app.label}: pluginExec: refreshing"
        refreshCallbacks()
        if (logEnable) log.debug "${app.label}: pluginExec: refreshed to: ${state.callbacks.size()}"
    }
    boolean skip = false
    state.callbacks.each { app_label, value ->
        if (value.functions.contains(func)) {
            if (logEnable) log.debug "${app.label}: pluginExec: $app_label ${state.callbacks[app_label].functions} ${childCache[app_label]}"
            def child = childCache[app_label] ?: getChildAppByLabel(app_label)
            childCache[app_label] = child
            def should_skip = child."$func"(arg)
            if (should_skip) skip = true
        }
    }
    return skip
}

def getSettings() {
    return settings
}
