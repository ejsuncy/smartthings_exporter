/**
 *  Copyright 2020 Joel Baranick
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Smartthings Exporter API
 *
 *  Author: ejsuncy
 */

import groovy.transform.EqualsAndHashCode

definition(
  name: "Smartthings Exporter API",
  namespace: "ejsuncy",
  author: "Joel Baranick",
  description: "API used by Smartthings_exporter to read sensor data.",
  category: "My Apps",
  iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
  iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
  oauth: [displayName: "Smartthings_exporter API", displayLink: ""])

preferences {
  section() {
    paragraph "Select the sensors you want the API to have access to."
  }
  section() {
    input "sensors", "capability.sensor", multiple: true, title: "Which sensors?", required: false
    input "actuators", "capability.actuator", multiple: true, title: "Which actuators?", required: false
    input "switches", "capability.switch", multiple: true, title: "Which switches?", required: false
  }
}

def installed() {
  initialize()
}

def updated() {
  unsubscribe()
  initialize()
}

def initialize() {
	def devices = getDevices()
    devices.each {
        def device = it.value
        def supportedCapabilities = device.capabilities
        supportedCapabilities.each {
        	if (attributeMappings.containsKey(it.name)) {
	            def attributeMapping = attributeMappings.get(it.name)
            	attributeMapping.each {
                	if (it.value.type == "counter") {
	                    state["${device.id}__${it.key}"] = 0
                    	it.value.values.each {value ->
                        	def eventName = "${it.key}.${value}"
    						log.debug("Subscribing to ${eventName} for device ${device}")
                    		subscribe(device, eventName, handleCounter)
                        }
                    }
                }
            }
        }
    }
}

mappings {
  path("/sensors") {
    action: [
      GET: "listSensors"
    ]
  }
}

def Map getAttributeMappings() {
    return [
    	"Alarm" : [
            "alarmState" : [
                name: "alarm_count",
                type: "counter",
                description: "Count of alarms.",
                values: ["siren", "strobe", "both"]
            ]
        ],
        "Battery" : [
            "battery": [
                name: "battery_percentage",
                type: "gauge",
                description: "Percentage of battery remaining.",
                conversion: this.&valueFloat
            ]
        ],
        "Carbon Monoxide Detector" : [
            "carbonMonoxide" : [
                name: "carbon_monoxide_detection_count",
                type: "counter",
                description: "Count of carbon monoxide detections.",
                values: ["detected"]
            ]
        ],
        "Color Temperature" : [
            "colorTemperature" : [
                name: "color_temperature_kelvins",
                type: "gauge",
                description: "Light color temperature.",
                conversion: this.&valueFloat
            ]
        ],
        "Contact Sensor" : [
            "contact" : [
                name: "contact_opened_count",
                type: "counter",
                description: "Count of times contact was opened.",
                values: ["open"]
            ]
        ],
        "Energy Meter" : [
            "energy" : [
                name: "energy_usage_joules",
                type: "gauge",
                description: "Energy usage in joules.",
                conversion: this.&valueJoules
            ]
        ],
        "Relative Humidity Measurement" : [
            "humidity" : [
                name: "relative_humidity_percentage",
                type: "gauge",
                description: "Current relative humidity percentage.",
                conversion: this.&valueFloat
            ]
        ],
        "Color Control" : [
            "hue" : [
                name: "color_hue_percentage",
                type: "gauge",
                description: "Light color hue percentage.",
                conversion: this.&valueFloat
            ],
            "saturation" : [
                name: "color_saturation_percentage",
                type: "gauge",
                description: "Light color saturation percentage.",
                conversion: this.&valueFloat
            ]
        ],
        "Illuminance Measurement" : [
            "illuminance" : [
                name: "illuminance_lux",
                type: "gauge",
                description: "Light illuminance in lux.",
                conversion: this.&valueFloat
            ]
        ],
        "Switch Level" : [
            "level" : [
                name: "level",
                type: "gauge",
                description: "Level as a percentage.",
                conversion: this.&valueFloat
            ]
        ],
        "Motion Sensor" : [
            "motion" : [
                name: "motion_detection_count",
                type: "counter",
                description: "Count of motion detections.",
                values: ["active"]
            ]
        ],
        "Power Meter" : [
            "power" : [
                name: "power_usage_watts",
                type: "gauge",
                description: "Current power usage in watts.",
                conversion: this.&valueFloat
            ]
        ],
        "Presence Sensor" : [
            "presence" : [
                name: "presence_detection_count",
                type: "counter",
                description: "Count of presence detections.",
                values: ["present"]
            ]
        ],
        "Smoke Detector" : [
            "smoke" : [
                name: "smoke_detection_count",
                type: "counter",
                description: "Count of smoke detections.",
                values: ["detected"]
            ]
        ],
        "Switch" : [
            "switch" : [
                name: "switch_enabled",
                type: "gauge",
                description: "1 if the switch is on.",
                conversion: this.&valueOnOff
            ]
        ],
        "Tamper Alert" : [
            "tamper" : [
                name: "tamper_detected_count",
                type: "counter",
                description: "Count of tamper detections.",
                values: ["detected"]
            ]
        ],
        "Temperature Measurement" : [
            "temperature" : [
                name: "temperature_fahrenheit",
                type: "gauge",
                description: "Temperature in fahrenheit.",
                conversion: this.&valueFloat
            ]
        ],
        "Voltage Measurement" : [
            "voltage" : [
                name: "voltage_volts",
                type: "gauge",
                description: "Energy voltage in Volts.",
                conversion: this.&valueFloat
            ]
        ]
    ]
}

def handleCounter(evt) {
	def stateId = "${evt.deviceId}__${evt.name}"
	state[stateId] = state[stateId] + 1
}

def listSensors() {
    def attributeMappings = getAttributeMappings()
    def descriptions = [:]
    def metrics = [:]
    def devices = getDevices()
    devices.each {
        def device = it.value
        def metricDescriptions = [:]
        def metric = [:]
        def metricAttributes = [:]
        def supportedCapabilities = device.capabilities
        supportedCapabilities.each {
        	if (attributeMappings.containsKey(it.name)) {
	            def attributeMapping = attributeMappings.get(it.name)
            	attributeMapping.each {
                	if (it.value.type == "gauge") {
                        def currentValue = device.currentValue(it.key)
                        if (currentValue != null) {
		                    metricDescriptions[it.value.name] = it.value.description
                            metricAttributes[it.value.name] = it.value.conversion(currentValue)
                        }
                    } else if (it.value.type == "counter") {
                        metricDescriptions[it.value.name] = it.value.description
                        metricAttributes[it.value.name] = state["${device.id}__${it.key}"]
                    }
                }
            }
        }
        if (metricAttributes.size() > 0) {
            ["name", "displayName"].each {
                metric[it] = device."$it"
            }
            metric.attributes = metricAttributes
            descriptions = descriptions << metricDescriptions
            metrics[device.id] = metric
        }
    }
    [descriptions: descriptions, sensors: metrics]
}

private getDevices() {
	def devices = [:]
    actuators.each {
      devices[it.id] = it
    }
    sensors.each {
      if(!devices.containsKey(it.id)) {
        devices[it.id] = it
      }
    }
    switches.each {
      if(!devices.containsKey(it.id)) {
        devices[it.id] = it
      }
    }
    devices
}

private valueFloat(value) {
    if (!value) {
        return 0.0
    }
    try {
        float f = Float.valueOf(value).floatValue();
        return f
    } catch (NumberFormatException nfe) {
        return 0.0
    }
}

private valueOneOf(value, options) {
    if (!value?.trim()) {
        return 0.0
    }
    if (value?.trim() == options[0]) {
        return 0.0
    }
    if (value?.trim() == options[1]) {
        return 1.0
    }
    return 0.0
}

private valueOnOff(value) {
    return valueOneOf(value, ["off", "on"])
}

private valueJoules(value) {
    return valueFloat(value) * 3600000
}