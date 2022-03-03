/**
 * Copyright (c) 2010-2022 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.goecharger.internal.handler;

import static org.openhab.binding.goecharger.internal.GoEChargerBindingConstants.*;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.measure.quantity.ElectricCurrent;
import javax.measure.quantity.Energy;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.http.HttpMethod;
import org.openhab.binding.goecharger.internal.GoEChargerBindingConstants;
import org.openhab.binding.goecharger.internal.api.GoEStatusResponseBaseDTO;
import org.openhab.binding.goecharger.internal.api.GoEStatusResponseDTO;
import org.openhab.binding.goecharger.internal.api.GoEStatusResponseV2DTO;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonSyntaxException;

/**
 * The {@link GoEChargerHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Samuel Brucksch - Initial contribution
 * @author Reinhard Plaim - Adapt to use API version 2
 */
@NonNullByDefault
public class GoEChargerHandler extends GoEChargerBaseHandler {

    private final Logger logger = LoggerFactory.getLogger(GoEChargerHandler.class);

    public GoEChargerHandler(Thing thing, HttpClient httpClient) {
        super(thing, httpClient);
    }

    @Override
    protected State getValue(String channelId, GoEStatusResponseBaseDTO goeResponseBase) {
        var state = super.getValue(channelId, goeResponseBase);
        if (state != UnDefType.UNDEF) {
            return state;
        }

        var goeResponse = (GoEStatusResponseDTO) goeResponseBase;
        switch (channelId) {
            case PWM_SIGNAL:
                if (goeResponse.pwmSignal == null) {
                    return UnDefType.UNDEF;
                }
                String pwmSignal = null;
                switch (goeResponse.pwmSignal) {
                    case 1:
                        pwmSignal = "READY_NO_CAR";
                        break;
                    case 2:
                        pwmSignal = "CHARGING";
                        break;
                    case 3:
                        pwmSignal = "WAITING_FOR_CAR";
                        break;
                    case 4:
                        pwmSignal = "CHARGING_DONE_CAR_CONNECTED";
                        break;
                    default:
                }
                return new StringType(pwmSignal);
            case ERROR:
                if (goeResponse.errorCode == null) {
                    return UnDefType.UNDEF;
                }
                String error = null;
                switch (goeResponse.errorCode) {
                    case 0:
                        error = "NONE";
                        break;
                    case 1:
                        error = "RCCB";
                        break;
                    case 3:
                        error = "PHASE";
                        break;
                    case 8:
                        error = "NO_GROUND";
                        break;
                    default:
                        error = "INTERNAL";
                        break;
                }
                return new StringType(error);
            case ACCESS_CONFIGURATION:
                if (goeResponse.accessConfiguration == null) {
                    return UnDefType.UNDEF;
                }
                String accessConfiguration = null;
                switch (goeResponse.accessConfiguration) {
                    case 0:
                        accessConfiguration = "OPEN";
                        break;
                    case 1:
                        accessConfiguration = "RFID";
                        break;
                    case 2:
                        accessConfiguration = "AWATTAR";
                        break;
                    case 3:
                        accessConfiguration = "TIMER";
                        break;
                    default:
                }
                return new StringType(accessConfiguration);
            case ALLOW_CHARGING:
                if (goeResponse.allowCharging == null) {
                    return UnDefType.UNDEF;
                }
                return goeResponse.allowCharging == 1 ? OnOffType.ON : OnOffType.OFF;
            case PHASES:
                if (goeResponse.energy == null) {
                    return UnDefType.UNDEF;
                }
                int count = 0;
                if (goeResponse.energy[4] > 0) { // current P1
                    count++;
                }
                if (goeResponse.energy[5] > 0) { // current P2
                    count++;
                }
                if (goeResponse.energy[6] > 0) { // current P3
                    count++;
                }
                return new DecimalType(count);
            case TEMPERATURE_CIRCUIT_BOARD:
                if (goeResponse.temperature == null) {
                    return UnDefType.UNDEF;
                }
                return new QuantityType<>(goeResponse.temperature, SIUnits.CELSIUS);
            case SESSION_CHARGE_CONSUMPTION:
                if (goeResponse.sessionChargeConsumption == null) {
                    return UnDefType.UNDEF;
                }
                return new QuantityType<>((Double) (goeResponse.sessionChargeConsumption / 360000d),
                        Units.KILOWATT_HOUR);
            case SESSION_CHARGE_CONSUMPTION_LIMIT:
                if (goeResponse.sessionChargeConsumptionLimit == null) {
                    return UnDefType.UNDEF;
                }
                return new QuantityType<>((Double) (goeResponse.sessionChargeConsumptionLimit / 10d),
                        Units.KILOWATT_HOUR);
            case TOTAL_CONSUMPTION:
                if (goeResponse.totalChargeConsumption == null) {
                    return UnDefType.UNDEF;
                }
                return new QuantityType<>((Double) (goeResponse.totalChargeConsumption / 10d), Units.KILOWATT_HOUR);
            case CURRENT_L1:
                if (goeResponse.energy == null) {
                    return UnDefType.UNDEF;
                }
                // values come in as A*10, 41 means 4.1A
                return new QuantityType<>((Double) (goeResponse.energy[4] / 10d), Units.AMPERE);
            case CURRENT_L2:
                if (goeResponse.energy == null) {
                    return UnDefType.UNDEF;
                }
                return new QuantityType<>((Double) (goeResponse.energy[5] / 10d), Units.AMPERE);
            case CURRENT_L3:
                if (goeResponse.energy == null) {
                    return UnDefType.UNDEF;
                }
                return new QuantityType<>((Double) (goeResponse.energy[6] / 10d), Units.AMPERE);
            case POWER_L1:
                if (goeResponse.energy == null) {
                    return UnDefType.UNDEF;
                }
                // values come in as kW*10, 41 means 4.1kW
                return new QuantityType<>(goeResponse.energy[7] * 100, Units.WATT);
            case POWER_L2:
                if (goeResponse.energy == null) {
                    return UnDefType.UNDEF;
                }
                return new QuantityType<>(goeResponse.energy[8] * 100, Units.WATT);
            case POWER_L3:
                if (goeResponse.energy == null) {
                    return UnDefType.UNDEF;
                }
                return new QuantityType<>(goeResponse.energy[9] * 100, Units.WATT);
        }
        return UnDefType.UNDEF;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            // we can not update single channels and refresh is triggered automatically
            // anyways
            return;
        }

        String key = null;
        String value = null;

        switch (channelUID.getId()) {
            case MAX_CURRENT:
                key = "amp";
                if (command instanceof DecimalType) {
                    value = String.valueOf(((DecimalType) command).intValue());
                } else if (command instanceof QuantityType<?>) {
                    value = String.valueOf(((QuantityType<ElectricCurrent>) command).toUnit(Units.AMPERE).intValue());
                }
                break;
            case SESSION_CHARGE_CONSUMPTION_LIMIT:
                key = "dwo";
                var multiplier = 10;
                if (command instanceof DecimalType) {
                    value = String.valueOf(((DecimalType) command).intValue() * multiplier);
                } else if (command instanceof QuantityType<?>) {
                    value = String.valueOf(
                            ((QuantityType<Energy>) command).toUnit(Units.KILOWATT_HOUR).intValue() * multiplier);
                }
                break;
            case ALLOW_CHARGING:
                key = "alw";
                if (command instanceof OnOffType) {
                    value = command == OnOffType.ON ? "1" : "0";
                }
                break;
            case ACCESS_CONFIGURATION:
                key = "ast";
                if (command instanceof StringType) {
                    switch (command.toString().toUpperCase()) {
                        case "OPEN":
                            value = "0";
                            break;
                        case "RFID":
                            value = "1";
                            break;
                        case "AWATTAR":
                            value = "2";
                            break;
                        case "TIMER":
                            value = "3";
                            break;
                        default:
                    }
                }
                break;
        }

        if (key != null && value != null) {
            sendData(key, value);
        } else {
            logger.warn("Could not update channel {} with key {} and value {}", channelUID.getId(), key, value);
        }
    }

    @Override
    public void initialize() {
        super.initialize();
    }

    private String getReadUrl() {
        return GoEChargerBindingConstants.API_URL.replace("%IP%", config.ip.toString());
    }

    private String getWriteUrl(String key, String value) {
        return GoEChargerBindingConstants.MQTT_URL.replace("%IP%", config.ip.toString()).replace("%KEY%", key)
                .replace("%VALUE%", value);
    }

    private void sendData(String key, String value) {
        String urlStr = getWriteUrl(key, value);
        logger.trace("POST URL = {}", urlStr);

        try {
            HttpMethod httpMethod = HttpMethod.POST;
            ContentResponse contentResponse = httpClient.newRequest(urlStr).method(httpMethod)
                    .timeout(5, TimeUnit.SECONDS).send();
            String response = contentResponse.getContentAsString();

            logger.trace("{} Response: {}", httpMethod.toString(), response);

            var statusCode = contentResponse.getStatus();
            if (!(statusCode == 200 || statusCode == 204)) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                        "Request response was unsuccessful");
                logger.debug("Could not send data, Response {}, StatusCode: {}", response, statusCode);
            }
        } catch (InterruptedException | TimeoutException | ExecutionException | JsonSyntaxException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
            logger.warn("Could not send data: {}, {}", urlStr, e.toString());
        }
    }

    /**
     * Request new data from Go-eCharger
     *
     * @return the Go-eCharger object mapping the JSON response or null in case of
     *         error
     * @throws ExecutionException
     * @throws TimeoutException
     * @throws InterruptedException
     */
    @Nullable
    @Override
    protected GoEStatusResponseBaseDTO getGoEData()
            throws InterruptedException, TimeoutException, ExecutionException, JsonSyntaxException {
        String urlStr = getReadUrl();
        logger.trace("GET URL = {}", urlStr);

        ContentResponse contentResponse = httpClient.newRequest(urlStr).method(HttpMethod.GET)
                .timeout(5, TimeUnit.SECONDS).send();

        String response = contentResponse.getContentAsString();
        logger.trace("GET Response: {}", response);

        if (config.apiVersion == 1) {
            return gson.fromJson(response, GoEStatusResponseDTO.class);
        }
        return gson.fromJson(response, GoEStatusResponseV2DTO.class);
    }

    @Override
    protected void updateChannelsAndStatus(@Nullable GoEStatusResponseBaseDTO goeResponse, @Nullable String message) {
        if (goeResponse == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.COMMUNICATION_ERROR, message);
            allChannels.forEach(channel -> updateState(channel, UnDefType.UNDEF));
        } else {
            updateStatus(ThingStatus.ONLINE, ThingStatusDetail.NONE);
            allChannels.forEach(channel -> updateState(channel, getValue(channel, (GoEStatusResponseDTO) goeResponse)));
        }
    }
}
