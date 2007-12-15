/* [{
* (C) Copyright 2007 Nicolas Carranza and individual contributors.
* See the jpen-copyright.txt file in the jpen distribution for a full
* listing of individual contributors.
*
* This file is part of jpen.
*
* jpen is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* jpen is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with jpen.  If not, see <http://www.gnu.org/licenses/>.
* }] */
package jpen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Queue;
import jpen.event.PenListener;

public class Pen extends PenState {

	public static final int DEFAULT_FREQUENCY=60;
	private int frequency;
	private MyThread thread;

	/** Tail of event queue. */
	private PenEvent lastDispatchedEvent=new PenEvent(this) {
		                                     @Override
		                                     void dispatch() { }}
	                                     ;
	/** Head of event queue. */
	private PenEvent lastScheduledEvent=lastDispatchedEvent;
	public final PenState lastScheduledState=new PenState();
	private final List<PenListener> listeners=new ArrayList<PenListener>();
	private PenListener[] listenersArray;

	private class MyThread
		extends Thread {
		final int period=1000/Pen.this.frequency;
		long beforeTime;
		long procTime;
		long waitTime;
		PenEvent event;
		boolean waitedNewEvents;
		volatile boolean waitingNewEvents;

		public synchronized void run() {
			try {
				while(Pen.this.thread==this) {
					waitedNewEvents=waitNewEvents();
					beforeTime=System.currentTimeMillis();
					if(waitedNewEvents)
						waitTime=0;
					else
						yield();
					while((event=lastDispatchedEvent.next)!=null) {
						event.dispatch();
						lastDispatchedEvent=event;
					}
					for(PenListener l:getListenersArray())
						l.penTock( (period+waitTime) - (System.currentTimeMillis()-beforeTime) );

					procTime=System.currentTimeMillis()-beforeTime;

					waitTime=period-procTime;
					if(waitTime>0) {
						wait(waitTime);
						waitTime=0;
					}
				}
			} catch(InterruptedException ex) {
				throw new Error(ex);
			}
		}

		private boolean waitNewEvents() throws InterruptedException {
			if(lastDispatchedEvent.next==null) {
				waitingNewEvents=true;
				wait();
				waitingNewEvents=false;
				return true;
			}
			return false;
		}

		void processNewEvents() {
			if(waitingNewEvents)
				synchronized(this) {
					notify();
				}
		}
	}

	Pen() {
		setFrequency(DEFAULT_FREQUENCY);
	}

	public void setFrequency(int frequency) {
		if(frequency<=0)
			throw new IllegalArgumentException();
		stop();
		this.frequency=frequency;
		thread=new MyThread();
		thread.start();
	}

	private void stop() {
		if(thread!=null) {
			MyThread oldThread=thread;
			thread=null;
			oldThread.processNewEvents(); // may be waiting for new events.
			synchronized(oldThread) {
				oldThread.notify(); // may be waiting the frequency period
				try {
					oldThread.join();
				} catch(InterruptedException ex) {
					throw new Error(ex);
				}
			}
		}
		frequency=-1;
	}

	public int getFrequency() {
		return frequency;
	}

	public void addListener(PenListener l) {
		synchronized(listeners) {
			listeners.add(l);
			listenersArray=null;
		}
	}

	public void removeListener(PenListener l) {
		synchronized(listeners) {
			listeners.remove(l);
			listenersArray=null;
		}
	}

	PenListener[] getListenersArray() {
		if(listenersArray==null)
			synchronized(listeners) {
				listenersArray=listeners.toArray(new PenListener[listeners.size()]);
			}
		return listenersArray;
	}

	private static class PhantomLevelFilter {
		public static int THRESHOLD_PERIOD=200;
		private PenDevice lastDevice; // last device NOT filtered
		private PLevelEvent lastEvent; // last event scheduled
		boolean filteredFirstInSecuence;

		boolean filter(PenDevice device) {
			if(!device.isDigitizer()) {
				if(lastDevice!=null &&
				        lastDevice!=device &&
				        System.currentTimeMillis()-lastEvent.time<=THRESHOLD_PERIOD)
					return true;
				if(!filteredFirstInSecuence) {
					filteredFirstInSecuence=true;
					return true;
				}
			} else
				filteredFirstInSecuence=false;
			lastDevice=device;
			return false;
		}

		void setLastEvent(PLevelEvent event) {
			this.lastEvent=event;
		}

		PLevelEvent getLastEvent() {
			return lastEvent;
		}
	}

	private final PhantomLevelFilter phantomLevelFilter=new PhantomLevelFilter();
	private final List<PLevel> scheduledLevels=new ArrayList<PLevel>();
	public boolean scheduleLevelEvent(PenDevice device, Collection<PLevel> levels) {
		synchronized(scheduledLevels) {
			if(phantomLevelFilter.filter(device))
				return false;
			for(PLevel level:levels) {
				if(level.value==lastScheduledState.getLevelValue(level.typeNumber))
					continue;
				scheduledLevels.add(level);
				lastScheduledState.setLevelValue(level);
			}
			if(!scheduledLevels.isEmpty()) {
				int newKindTypeNumber=device.getKindTypeNumber();
				if(lastScheduledState.getKindTypeNumber()!=newKindTypeNumber) {
					lastScheduledState.setKindTypeNumber(newKindTypeNumber);
					schedule(new PKindEvent(this, new PKind(newKindTypeNumber)));
				}
				PLevelEvent levelEvent=new PLevelEvent(this,
				                                       scheduledLevels.toArray(new PLevel[scheduledLevels.size()]));
				phantomLevelFilter.setLastEvent(levelEvent);
				schedule(levelEvent);
				scheduledLevels.clear();
				return true;
			}
			return false;
		}
	}

	private final Object buttonsLock=new Object();
	public void scheduleButtonEvent(PButton button) {
		synchronized(buttonsLock) {
			if(lastScheduledState.setButtonValue(button))
				schedule(new PButtonEvent(this, button));
		}
	}

	public void scheduleScrollEvent(PScroll scroll) {
		schedule(new PScrollEvent(this, scroll));
	}

	private final void schedule(PenEvent ev) {
		synchronized(lastScheduledEvent) {
			ev.time=System.currentTimeMillis();
			lastScheduledEvent.next=ev;
			lastScheduledEvent=ev;
			if(thread!=null) thread.processNewEvents();
		}
	}
}