/* [{
Copyright 2007, 2008 Nicolas Carranza <nicarran at gmail.com>

This file is part of jpen.

jpen is free software: you can redistribute it and/or modify
it under the terms of the GNU Lesser General Public License as published by
the Free Software Foundation, either version 3 of the License,
or (at your option) any later version.

jpen is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public License
along with jpen.  If not, see <http://www.gnu.org/licenses/>.
}] */
package jpen;

import java.awt.Component;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Logger;
import java.util.Map;
import java.util.Set;
import jpen.event.PenManagerListener;
import jpen.provider.osx.CocoaProvider;
import jpen.provider.system.SystemProvider;
import jpen.provider.wintab.WintabProvider;
import jpen.provider.xinput.XinputProvider;

public final class PenManager {
	private static final Logger L=Logger.getLogger(PenManager.class.getName());
	
	public final Pen  pen=new Pen();
	public final Component component;
	private final Set<PenProvider.Constructor> providerConstructors=new HashSet<PenProvider.Constructor>();
	private final Set<PenProvider.Constructor> providerConstructorsA=Collections.unmodifiableSet(providerConstructors);
	private final Map<Byte, PenDevice> deviceIdToDevice=new HashMap<Byte, PenDevice>();
	private final Collection<PenDevice> devicesA=Collections.unmodifiableCollection(deviceIdToDevice.values());
	private byte nextDeviceId;
	private volatile boolean paused;
	private final List<PenManagerListener> listeners=new ArrayList<PenManagerListener>();
	private final DragOutHandler dragOutHandler;

	public PenManager(Component component) {
		this.component=component;
		addProvider(new SystemProvider.Constructor());
		addProvider(new XinputProvider.Constructor());
		addProvider(new WintabProvider.Constructor());
		addProvider(new CocoaProvider.Constructor());
		dragOutHandler=new DragOutHandler(this);
	}

	/**
	Constructs and adds provider if {@link PenProvider.Constructor#constructable()} is true.
	@return The {@link PenProvider} added or null if it couldn't be constructed.
	*/
	public PenProvider addProvider(PenProvider.Constructor providerConstructor) {
		if(providerConstructor.constructable()) {
			if(!this.providerConstructors.add(providerConstructor))
				throw new IllegalArgumentException("constructor already added");
			if(providerConstructor.construct(this)){
				PenProvider provider=providerConstructor.getConstructed();
				for(PenDevice device:provider.getDevices())
					firePenDeviceAdded(providerConstructor, device);
				return provider;
			}
		}
		return null;
	}

	public void addListener(PenManagerListener l) {
		synchronized(listeners) {
			listeners.add(l);
		}
	}

	public void removeListener(PenManagerListener l) {
		synchronized(listeners) {
			listeners.remove(l);
		}
	}

	public void firePenDeviceAdded(PenProvider.Constructor constructor, PenDevice device) {
		synchronized(listeners) {
			byte nextDeviceId=getNextDeviceId();
			device.setId(nextDeviceId);
			if(deviceIdToDevice.put(nextDeviceId, device)!=null)
				throw new AssertionError();
			for(PenManagerListener l: listeners){
				l.penDeviceAdded(constructor, device);
			}
		}
	}

	private byte getNextDeviceId(){
		Set<Byte> deviceIds=deviceIdToDevice.keySet();
		while(deviceIds.contains(Byte.valueOf(nextDeviceId)))
			nextDeviceId++;
		return nextDeviceId;
	}

	public void firePenDeviceRemoved(PenProvider.Constructor constructor, PenDevice device) {
		synchronized(listeners) {
			if(deviceIdToDevice.remove(device.getId())==null)
				throw new IllegalArgumentException("device not found");
			for(PenManagerListener l: listeners)
				l.penDeviceRemoved(constructor, device);
		}
	}

	public PenDevice getDevice(byte deviceId){
		return deviceIdToDevice.get(Byte.valueOf(deviceId));
	}

	public Collection<PenDevice> getDevices(){
		return devicesA;
	}

	public Set<PenProvider.Constructor> getConstructors() {
		return providerConstructorsA;
	}

	/**
	@deprecated use {@link PenProvider.Constructor#getConstructed()}
	*/
	@Deprecated
	public PenProvider getProvider(PenProvider.Constructor constructor) {
		return constructor.getConstructed();
	}
	/**
	@deprecated use {@link PenProvider.Constructor#getConstructionException()}
	*/
	@Deprecated
	public PenProvider.ConstructionException getConstructionException(PenProvider.Constructor constructor) {
		return constructor.getConstructionException();
	}

	void setPaused(boolean paused) {
		if(this.paused==paused)
			return;
		this.paused=paused;
		if(paused)
			pen.scheduleButtonReleasedEvents();
		for(PenProvider.Constructor providerConstructor: providerConstructors){
			PenProvider penProvider=providerConstructor.getConstructed();
			if(penProvider!=null)
				penProvider.penManagerPaused(paused);
		}
	}

	public boolean getPaused() {
		return paused;
	}

	public void scheduleButtonEvent(PButton button) {
		if(paused)
			return;
		pen.scheduleButtonEvent(button);
	}

	public void scheduleScrollEvent(PenDevice device, PScroll scroll) {
		if(paused)
			return;
		pen.scheduleScrollEvent(device, scroll);
	}

	public boolean scheduleLevelEvent(PenDevice device, Collection<PLevel> levels) {
		return scheduleLevelEvent(device, levels, System.currentTimeMillis());
	}

	public boolean scheduleLevelEvent(PenDevice device, Collection<PLevel> levels, long deviceTime) {
		if(paused)
			return false;
		switch(dragOutHandler.getMode()){
		case DISABLED:
			return pen.scheduleLevelEvent(device, deviceTime, levels, 0, component.getWidth(), 0, component.getHeight());
		case ENABLED:
			return pen.scheduleLevelEvent(device, deviceTime, levels,  -Integer.MAX_VALUE, Integer.MAX_VALUE, -Integer.MAX_VALUE, Integer.MAX_VALUE);
		default:
			throw new AssertionError();
		}
	}
}