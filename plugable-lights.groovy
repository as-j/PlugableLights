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
    [
        appName: "Plugable Lights: Do not adjust Plugin",
        name: "plugInDoNotAdjust",
        title: "Don't change light levels/settings once on",
    ],
    [
        appName: "Plugable Lights: Time Plugin",
        name: "plugInTime",
        title: "Time Plugin",
    ],
    [
        appName: "Plugable Lights: Turn On of Off At A Set Time",
        name: "plugInTimedOnOff",
        title: "Turn on/off at a set time",
    ],
]

// getChildApps seemms to be slow, ~40ms so cache child app
@Field static Map childCache = [:]

definition(
    name: "Plugable Lights Motion triggered",
    namespace: "asj",
    author: "asj",
    description: "Motion or Contact triggered light with plugable child apps",
    iconUrl: "",
    iconX2Url: "") {

    preferences {
        page(name: "mainPage", title: "Settings Page", install: true, uninstall: true) {
            section("App Name") {
                label title: "App Name", defaultValue: app.label, required: true, submitOnChange: true
            }
            section("Turn On Trigger") {
                input "turnOnMotionSensor", "capability.motionSensor", title: "Motion Sensor Becomes Active", multiple: true, required: false, submitOnChange: true
                input "turnOnContactSensor", "capability.contactSensor", title: "Contacts Open", multiple: true, required: false, submitOnChange: true
            }
            section("Turn Off Triggers") {
                input "timeOffS", "number", title: "Turn off after motion ends or contact closes (s)", defaultValue: 120, submitOnChange: true
                input "turnOffMotionSensor", "capability.motionSensor", title: "Motion Sensor Becomes Inactive", multiple: true, submitOnChange: true
                input "turnOffContactSensor", "capability.contactSensor", title: "Contacts Close", multiple: true, submitOnChange: true
            }
            section("Defaults for all modes") {
                input "level_default", "number", title: "Default Level", require: false, submitOnChange: true
                input "temp_default", "number", title: "Default Temp", require: false, submitOnChange: true
                input "switch_default", "capability.switch", title: "Devices To Turn On", multiple: true, required: false, submitOnChange: true
                input "switch_off_default", "capability.switch", title: "Devices To Turn Off", multiple: true, required: false, submitOnChange: true
                input "onViaSetLevel", "bool", title: "Turn on with setLevel Only (zooz)", defaultValue: false, submitOnChange: true
            }
            section("Plugins") {
                plugIns.each { 
                    app name: it.name, appName: it.appName, namespace: it?.namespace ?: "asj", title: it.title, submitOnChange: true
                }
            }
            section("Debug Settings") {
                //standard logging options
                input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false, submitOnChange: true
                input name: "txtEnable", type: "bool", title: "Enable info logging", defaultValue: false, submitOnChange: true
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
    if (textEnable) log.info "${app.label}: updated ${settings}"

    setupSubscriptions()
 
    pluginExec("parentUpdated", settings)
    runIn(1, refreshCallbacks)
}

def clearScheduledEvents() {
    unschedule("turnOff")
}

def clearSubscriptions() {
    if (txtEnable) log.info "${app.label}: clearSubscriptions"
    unsubscribe()
}

def setupSubscriptions() {
    if (txtEnable) log.info "${app.label}: setupSubscriptions"
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
        if (logEnable) log.debug "updated(): ${device.currentValue("level")}"
    }
}

def turnOnDevices(values) {
    values.devices.each { device ->
        if (logEnable) log.debug "turnOnEvent(): turning on $device"

        def skip = pluginExec("turnOn", device)
        if (skip) return

        if (values.mode_level && device.hasCommand("setLevel")) {
            if (logEnable) log.debug "turnOnEvent(): has setLevel ${device.currentValue('level')} want: ${values.mode_level}"
            if (txtEnable && (device.currentLevel != values.mode_level)) log.info "turnOnEvent(): has setLevel ${device.currentLevel} want: ${values.mode_level}"
            if (settings.onViaSetLevel || (device.currentValue('level') != values.mode_level)) {
                device.setLevel(values.mode_level)
            }
        }
        if (values.mode_temp && device.hasCommand("setColorTemperature")) {
            if (txtEnable && (device.currentColorTemperature != values.mode_temp)) log.info "turnOnEvent(): has setTemp ${device.currentColorTemperature} want: ${values.mode_temp}"
            if (device.currentValue('colorTemperature') != values.mode_temp) device.setColorTemperature(values.mode_temp)
        }
        if (txtEnable && (device.currentValue('switch') != "on")) log.info "turnOnEvent(): switch ${device.currentSwitch} -> on"
        if (!settings.onViaSetLevel && (device.currentValue('switch') != "on")) device.on()
    }

    values.devices_off.each { device ->
        if (logEnable) log.debug "turnOnEvent(): turning off $device"
        if (device.currentValue('switch') != "off") device.off()
    }
}

def turnOnEvent(evt) {
    def start = now()
    def values = [
        devices: settings.switch_default,
        devices_off: settings.switch_off_default,
        mode_level: settings.level_default,
        mode_temp: settings.temp_default,
    ]

    if (txtEnable) log.info "turnOnEvent(): $evt.displayName($evt.name) $evt.value values: $values"

    def skip = pluginExec("preTurnOn", values)

    if (logEnable) log.debug "turnOnEvent(): skip: $skip new values: $values $skip"
    if (txtEnable && skip) log.info "turnOnEvent(): skipping due to plugin. skip: $skip"
 
    if (!skip) {
        turnOnDevices(values)

        def text ="${app.label} turn on event"
        if (txtEnable) log.info text
        sendEvent(name: "switch", value: "on", descriptionText: text)
    }

    pluginExec("postTurnOn", values)
    
    unschedule("turnOff")

    def delta = now() - start
    if (logEnable) log.debug "turnOnEvent(): time: $delta"
}

def turnOffEvent(evt) {
    if (txtEnable) log.info "turnOffEvent(): $evt.displayName($evt.name) $evt.value"

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
    if (skip && txtEnable) log.info "turnOffEvent(): skipping due to plugin"
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
    if (txtEnable) log.info "turnOff() ${settings.switch_default}"

    def values = [
        devices: settings.switch_default,
    ]

    def skip = pluginExec("preTurnOff", values)
    if (logEnable) log.debug "turnOff() skip: $skip new values ${values}"
    if (txtEnable && skip) log.info "turnOff(): skipping due to plugin"
    if (skip) return


    def num = 0
    values.devices.each { device ->
        if (logEnable) log.debug "turnOff(): turning off $device"
        device.off()
        num += 1
    }

    def text ="${app.label} turn off event (${num})"
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
        //if (logEnable) log.debug "${app.label}: pluginExec: refreshing"
        refreshCallbacks()
        if (logEnable) log.debug "${app.label}: pluginExec: refreshed to: ${state.callbacks.size()}"
    }
    boolean skip = false
    state.callbacks.each { app_label, value ->
        if (value.functions.contains(func)) {
            if (logEnable) log.debug "${app.label}: pluginExec: $app_label ${state.callbacks[app_label].functions} ${childCache[app_label]}"
            def child = childCache[app_label] ?: getChildAppByLabel(app_label)
            childCache[app_label] = child
            if (txtEnable) log.info "${app.label}: pluginExec: $app_label -> $func"
            def should_skip = child?."$func"(arg)
            if (should_skip) {
                if (txtEnable) log.info "${app.label}: pluginExec: $app_label -> $func returned SKIP"
                skip = true
            }
        }
    }
    return skip
}

def getSettings() {
    return settings
}

