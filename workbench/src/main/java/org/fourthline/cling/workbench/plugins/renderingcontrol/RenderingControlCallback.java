/*
 * Copyright (C) 2011 4th Line GmbH, Switzerland
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 2 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.fourthline.cling.workbench.plugins.renderingcontrol;

import org.fourthline.cling.controlpoint.SubscriptionCallback;
import org.fourthline.cling.model.gena.CancelReason;
import org.fourthline.cling.model.gena.GENASubscription;
import org.fourthline.cling.model.message.UpnpResponse;
import org.fourthline.cling.model.meta.Service;
import org.fourthline.cling.model.types.UnsignedIntegerFourBytes;
import org.fourthline.cling.support.lastchange.LastChange;
import org.fourthline.cling.support.model.Channel;
import org.fourthline.cling.support.renderingcontrol.lastchange.RenderingControlLastChangeParser;
import org.fourthline.cling.support.renderingcontrol.lastchange.RenderingControlVariable;
import org.fourthline.cling.workbench.Workbench;
import org.seamless.swing.logging.LogMessage;

import javax.swing.SwingUtilities;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Christian Bauer
 */
abstract public class RenderingControlCallback extends SubscriptionCallback {

    private static Logger log = Logger.getLogger(RenderingControlCallback.class.getName());

    public RenderingControlCallback(Service service) {
        super(service);
    }

    @Override
    protected void failed(GENASubscription subscription,
                          UpnpResponse responseStatus,
                          Exception exception,
                          String defaultMsg) {
        log.severe(defaultMsg);
    }

    public void established(GENASubscription subscription) {
        Workbench.log(new LogMessage(
                Level.INFO,
                "RenderingControl ControlPoint",
                "Subscription with service established, listening for events."
        ));
    }

    public void ended(GENASubscription subscription, final CancelReason reason, UpnpResponse responseStatus) {
        Workbench.log(new LogMessage(
                reason != null ? Level.WARNING : Level.INFO,
                "RenderingControl ControlPoint",
                "Subscription with service ended. " + (reason != null ? "Reason: " + reason : "")
        ));
        onDisconnect(reason);
    }

    public void eventReceived(GENASubscription subscription) {
        log.finer("Event received, sequence number: " + subscription.getCurrentSequence());

        final LastChange lastChange;
        try {
            lastChange = new LastChange(
                    new RenderingControlLastChangeParser(),
                    subscription.getCurrentValues().get("LastChange").toString()
            );
        } catch (Exception ex) {
            log.warning("Error parsing LastChange event content: " + ex);
            return;
        }

        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                for (UnsignedIntegerFourBytes instanceId : lastChange.getInstanceIDs()) {

                    log.finer("Processing LastChange event values for instance: " + instanceId);
                    RenderingControlVariable.Volume volume = lastChange.getEventedValue(
                            instanceId,
                            RenderingControlVariable.Volume.class
                    );
                    if (volume != null && volume.getValue().getChannel().equals(Channel.Master)) {
                        log.finer("Received new volume value for 'Master' channel: " + volume.getValue());
                        onMasterVolumeChanged(
                                new Long(instanceId.getValue()).intValue(),
                                volume.getValue().getVolume()
                        );
                    }
                }
            }
        });
    }

    public void eventsMissed(GENASubscription subscription, int numberOfMissedEvents) {
        log.warning("Events missed (" + numberOfMissedEvents + "), consider restarting this control point!");
    }

    abstract protected void onDisconnect(CancelReason reason);

    abstract protected void onMasterVolumeChanged(int instanceId, int newVolume);
}
