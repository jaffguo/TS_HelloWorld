/*
Copyright 2015, [name of copyright owner, Johannes Mulder (Fraunhofer IOSB)"]

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package de.fraunhofer.iosb.tc_lib_helloworld;

import de.fraunhofer.iosb.tc_lib.IVCT_BaseModel;
import de.fraunhofer.iosb.tc_lib.IVCT_RTIambassador;
import hla.rti1516e.AttributeHandle;
import hla.rti1516e.AttributeHandleSet;
import hla.rti1516e.AttributeHandleValueMap;
import hla.rti1516e.CallbackModel;
import hla.rti1516e.FederateAmbassador;
import hla.rti1516e.FederateHandle;
import hla.rti1516e.InteractionClassHandle;
import hla.rti1516e.LogicalTime;
import hla.rti1516e.MessageRetractionHandle;
import hla.rti1516e.ObjectClassHandle;
import hla.rti1516e.ObjectInstanceHandle;
import hla.rti1516e.OrderType;
import hla.rti1516e.ParameterHandle;
import hla.rti1516e.ParameterHandleValueMap;
import hla.rti1516e.TransportationTypeHandle;
import hla.rti1516e.encoding.DecoderException;
import hla.rti1516e.encoding.EncoderFactory;
import hla.rti1516e.encoding.HLAfloat32LE;
import hla.rti1516e.encoding.HLAunicodeString;
import hla.rti1516e.exceptions.AlreadyConnected;
import hla.rti1516e.exceptions.AttributeNotDefined;
import hla.rti1516e.exceptions.CallNotAllowedFromWithinCallback;
import hla.rti1516e.exceptions.ConnectionFailed;
import hla.rti1516e.exceptions.FederateHandleNotKnown;
import hla.rti1516e.exceptions.FederateInternalError;
import hla.rti1516e.exceptions.FederateNotExecutionMember;
import hla.rti1516e.exceptions.FederateServiceInvocationsAreBeingReportedViaMOM;
import hla.rti1516e.exceptions.InteractionClassNotDefined;
import hla.rti1516e.exceptions.InvalidFederateHandle;
import hla.rti1516e.exceptions.InvalidInteractionClassHandle;
import hla.rti1516e.exceptions.InvalidLocalSettingsDesignator;
import hla.rti1516e.exceptions.InvalidObjectClassHandle;
import hla.rti1516e.exceptions.NameNotFound;
import hla.rti1516e.exceptions.NotConnected;
import hla.rti1516e.exceptions.ObjectClassNotDefined;
import hla.rti1516e.exceptions.RTIinternalError;
import hla.rti1516e.exceptions.RestoreInProgress;
import hla.rti1516e.exceptions.SaveInProgress;
import hla.rti1516e.exceptions.UnsupportedCallbackModel;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;


/**
 * @author mul (Fraunhofer IOSB)
 */
public class HelloWorldBaseModel extends IVCT_BaseModel {

    private AttributeHandle                                _attributeIdName;
    private AttributeHandle                                _attributeIdPopulation;
    private boolean                                        receivedInteraction = false;
    private boolean                                        receivedReflect = false;
    private EncoderFactory                                 _encoderFactory;
    private InteractionClassHandle                         messageId;
    private IVCT_RTIambassador                             ivct_rti;
    private Logger                                         logger;
    private ParameterHandle                                parameterIdSender;
    private ParameterHandle                                parameterIdText;
    private String                                         message;
    private final Map<ObjectInstanceHandle, CountryValues> knownObjects        = new HashMap<ObjectInstanceHandle, CountryValues>();

    private static class CountryValues {
        private final String countryName;
        private float        prevPopulation = 0;
        private float        currPopulation = 0;


        CountryValues(final String name) {
            this.countryName = name;
        }


        @Override
        public String toString() {
            return this.countryName;
        }


        public float getPopulation() {
            return this.currPopulation;
        }


        public void setPopulation(final float population) {
            this.prevPopulation = this.currPopulation;
            this.currPopulation = population;
        }


        public boolean testPopulation(final float delta, final Logger logger) {
            final float min = this.prevPopulation * delta * (float) 0.99;
            final float mid = this.prevPopulation * delta;
            final float max = this.prevPopulation * delta * (float) 1.01;

            logger.info("---------------------------------------------------------------------");
            logger.info("testPopulation: test value received " + this.currPopulation + " in range " + mid + " +/-1%");
            logger.info("---------------------------------------------------------------------");
            if (this.currPopulation > min && this.currPopulation < max) {
                return false;
            }

            return true;
        }

    }


    /**
     * @param logger reference to a logger
     * @param ivct_rti reference to the RTI ambassador
     */
    public HelloWorldBaseModel(final Logger logger, final IVCT_RTIambassador ivct_rti) {
        super(ivct_rti, logger);
        this.logger = logger;
        this.ivct_rti = ivct_rti;
        this._encoderFactory = ivct_rti.getEncoderFactory();
    }


    /**
     * @param federateHandle the federate handle
     * @return the federate name or null
     */
    public String getFederateName(final FederateHandle federateHandle) {
        try {
            return this.ivct_rti.getFederateName(federateHandle);
        }
        catch (InvalidFederateHandle | FederateHandleNotKnown | FederateNotExecutionMember | NotConnected | RTIinternalError ex) {
            // TODO Auto-generated catch block
            ex.printStackTrace();
            return null;
        }
    }


    /**
     * @return false if a message received, true otherwise
     */
    public boolean getInteractionMessageStatus() {
        for (int j = 0; j < 100; j++) {
            if (this.receivedInteraction) {
                return false;
            }
            try {
                Thread.sleep(20);
            }
            catch (final InterruptedException ex) {
                continue;
            }
        }
        return true;
    }

    /**
     * @return false if a message received, true otherwise
     */
    public boolean getReflectMessageStatus() {
        for (int j = 0; j < 100; j++) {
            if (this.receivedReflect) {
                return false;
            }
            try {
                Thread.sleep(20);
            }
            catch (final InterruptedException ex) {
                continue;
            }
        }
        return true;
    }

    /**
     * @return the message received
     */
    public String getMessage() {
        this.receivedInteraction = false;
        return this.message;
    }


    /**
     * @return the parameter id text received
     */
    public ParameterHandle getParameterIdText() {
        return this.parameterIdText;
    }

    /**
     * @return the parameter id text received
     */
    public ParameterHandle getParameterIdSender() {
        return this.parameterIdSender;
    }


    /**
     * @return the message id
     */
    public InteractionClassHandle getMessageId() {
        return this.messageId;
    }


    /**
     * {@inheritDoc}
     */
    public void connect(final FederateAmbassador federateReference, final CallbackModel callbackModel, final String localSettingsDesignator) {
        try {
            this.ivct_rti.connect(federateReference, callbackModel, localSettingsDesignator);
        }
        catch (ConnectionFailed | InvalidLocalSettingsDesignator | UnsupportedCallbackModel | AlreadyConnected | CallNotAllowedFromWithinCallback | RTIinternalError ex) {
            // TODO Auto-generated catch block
            ex.printStackTrace();
        }
    }


    /**
     * @param sleepTime time to sleep
     * @return true means problem, false is ok
     */
    public boolean sleepFor(final long sleepTime) {
        try {
            Thread.sleep(sleepTime);
        }
        catch (final InterruptedException ex) {
            return true;
        }

        return false;
    }


    /**
     * @return true means error, false means correct
     */
    public boolean init() {

        // Subscribe and publish interactions
        try {
            this.messageId = this.ivct_rti.getInteractionClassHandle("Communication");
            this.parameterIdText = this.ivct_rti.getParameterHandle(this.messageId, "Message");
            this.parameterIdSender = this.ivct_rti.getParameterHandle(this.messageId, "Sender");
        }
        catch (NameNotFound | FederateNotExecutionMember | NotConnected | RTIinternalError | InvalidInteractionClassHandle ex1) {
            this.logger.error("Cannot get interaction class handle or parameter handle");
            return true;
        }

        try {
            this.ivct_rti.subscribeInteractionClass(this.messageId);
            this.ivct_rti.publishInteractionClass(this.messageId);
        }
        catch (FederateServiceInvocationsAreBeingReportedViaMOM | InteractionClassNotDefined | SaveInProgress | RestoreInProgress | FederateNotExecutionMember | NotConnected | RTIinternalError ex1) {
            // TODO Auto-generated catch block
            ex1.printStackTrace();
        }

        // Subscribe and publish objects
        ObjectClassHandle participantId;
        try {
            participantId = this.ivct_rti.getObjectClassHandle("Country");
            this._attributeIdName = this.ivct_rti.getAttributeHandle(participantId, "Name");
            this._attributeIdPopulation = this.ivct_rti.getAttributeHandle(participantId, "Population");
            this._attributeIdName = this.ivct_rti.getAttributeHandle(participantId, "Name");
        }
        catch (NameNotFound | FederateNotExecutionMember | NotConnected | RTIinternalError | InvalidObjectClassHandle ex) {
            this.logger.error("Cannot get object class handle or attribute handle");
            return true;
        }

        AttributeHandleSet attributeSet;
        try {
            attributeSet = this.ivct_rti.getAttributeHandleSetFactory().create();
            attributeSet.add(this._attributeIdName);
            attributeSet.add(this._attributeIdPopulation);
        }
        catch (FederateNotExecutionMember | NotConnected ex) {
            this.logger.error("Cannot build attribute set");
            return true;
        }

        try {
            // Only need to subscribe to the object class
            this.ivct_rti.subscribeObjectClassAttributes(participantId, attributeSet);
        }
        catch (AttributeNotDefined | ObjectClassNotDefined | SaveInProgress | RestoreInProgress | FederateNotExecutionMember | NotConnected | RTIinternalError ex) {
            this.logger.error("Cannot publish/subscribe attributes");
            return true;
        }

        return false;
    }


    /**
     * @param countryName the name of the tested country
     * @param delta the rate at which the population should be increasing
     * @return true means error, false means correct
     */
    public boolean testCountryPopulation(final String countryName, final float delta) {
        for (final Map.Entry<ObjectInstanceHandle, CountryValues> entry: this.knownObjects.entrySet()) {
            if (entry.getValue().toString().equals(countryName)) {
                if (entry.getValue().testPopulation(delta, this.logger)) {
                    this.logger.error("testCountryPopulation test failed");
                    return true;
                }
                return false;
            }
        }

        return true;
    }


    private void doReceiveInteraction(final InteractionClassHandle interactionClass, final ParameterHandleValueMap theParameters) {
        if (interactionClass.equals(this.messageId)) {
            if (!theParameters.containsKey(this.parameterIdText)) {
                System.out.println("Bad message received: No text.");
                return;
            }
            try {
                final HLAunicodeString messageDecoder = this._encoderFactory.createHLAunicodeString();
                messageDecoder.decode(theParameters.get(this.parameterIdText));
                this.message = messageDecoder.getValue();
            }
            catch (final DecoderException e) {
                System.out.println("Failed to decode incoming interaction");
            }
        }

        this.receivedInteraction = true;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void receiveInteraction(final InteractionClassHandle interactionClass, final ParameterHandleValueMap theParameters, final byte[] userSuppliedTag, final OrderType sentOrdering, final TransportationTypeHandle theTransport, final SupplementalReceiveInfo receiveInfo) throws FederateInternalError {
        this.doReceiveInteraction(interactionClass, theParameters);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void receiveInteraction(final InteractionClassHandle interactionClass, final ParameterHandleValueMap theParameters, final byte[] userSuppliedTag, final OrderType sentOrdering, final TransportationTypeHandle theTransport, final LogicalTime theTime, final OrderType receivedOrdering, final SupplementalReceiveInfo receiveInfo) throws FederateInternalError {
        this.doReceiveInteraction(interactionClass, theParameters);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void receiveInteraction(final InteractionClassHandle interactionClass, final ParameterHandleValueMap theParameters, final byte[] userSuppliedTag, final OrderType sentOrdering, final TransportationTypeHandle theTransport, final LogicalTime theTime, final OrderType receivedOrdering, final MessageRetractionHandle retractionHandle, final SupplementalReceiveInfo receiveInfo) throws FederateInternalError {
        this.doReceiveInteraction(interactionClass, theParameters);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void discoverObjectInstance(final ObjectInstanceHandle theObject, final ObjectClassHandle theObjectClass, final String objectName) throws FederateInternalError {

        if (!this.knownObjects.containsKey(theObject)) {
            final CountryValues member = new CountryValues(objectName);
            this.knownObjects.put(theObject, member);
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void removeObjectInstance(final ObjectInstanceHandle theObject, final byte[] userSuppliedTag, final OrderType sentOrdering, final FederateAmbassador.SupplementalRemoveInfo removeInfo) {
        final CountryValues member = this.knownObjects.remove(theObject);
        if (member != null) {
            this.logger.info("[" + member + " has left]");
        }
    }


    /**
     * @param theObject the object instance handle
     * @param theAttributes the map of attribute handle / value
     */
    public void doReflectAttributeValues(final ObjectInstanceHandle theObject, final AttributeHandleValueMap theAttributes) {
        if (theAttributes.containsKey(this._attributeIdName) && theAttributes.containsKey(this._attributeIdPopulation)) {
            try {
                CountryValues cv;
                final HLAunicodeString usernameDecoder = this._encoderFactory.createHLAunicodeString();
                usernameDecoder.decode(theAttributes.get(this._attributeIdName));
                final String memberName = usernameDecoder.getValue();
                final HLAfloat32LE populationDecoder = this._encoderFactory.createHLAfloat32LE();
                populationDecoder.decode(theAttributes.get(this._attributeIdPopulation));
                final float population = populationDecoder.getValue();
                this.logger.info("Population: " + population);
                if (this.knownObjects.containsKey(theObject)) {
                    cv = this.knownObjects.get(theObject);
                    if (cv.toString().equals(memberName) == false) {
                        this.logger.error("Country name not equal to country attribute name " + cv.toString() + " " + memberName);
                    }
                    cv.setPopulation(population);
                }
            }
            catch (final DecoderException e) {
                this.logger.error("Failed to decode incoming attribute");
                return;
            }
            receivedReflect = true;
        }
    }


    /**
     * @param theObject the object instance handle
     * @param theAttributes the map of attribute handle / value
     */
    @Override
    public void reflectAttributeValues(final ObjectInstanceHandle theObject, final AttributeHandleValueMap theAttributes, final byte[] userSuppliedTag, final OrderType sentOrdering, final TransportationTypeHandle theTransport, final SupplementalReflectInfo reflectInfo) throws FederateInternalError {
        this.doReflectAttributeValues(theObject, theAttributes);
    }


    /**
     * @param theObject the object instance handle
     * @param theAttributes the map of attribute handle / value
     */
    @Override
    public void reflectAttributeValues(final ObjectInstanceHandle theObject, final AttributeHandleValueMap theAttributes, final byte[] userSuppliedTag, final OrderType sentOrdering, final TransportationTypeHandle theTransport, final LogicalTime theTime, final OrderType receivedOrdering, final SupplementalReflectInfo reflectInfo) throws FederateInternalError {
        this.doReflectAttributeValues(theObject, theAttributes);
    }


    /**
     * @param theObject the object instance handle
     * @param theAttributes the map of attribute handle / value
     */
    @Override
    public void reflectAttributeValues(final ObjectInstanceHandle theObject, final AttributeHandleValueMap theAttributes, final byte[] userSuppliedTag, final OrderType sentOrdering, final TransportationTypeHandle theTransport, final LogicalTime theTime, final OrderType receivedOrdering, final MessageRetractionHandle retractionHandle, final SupplementalReflectInfo reflectInfo) throws FederateInternalError {
        this.doReflectAttributeValues(theObject, theAttributes);
    }

}
