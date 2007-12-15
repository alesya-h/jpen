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
package jpen.provider.system;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.Arrays;
import java.util.List;
import jpen.PButton;
import jpen.PButtonEvent;
import jpen.PenDevice;
import jpen.PenEvent;
import jpen.PenManager;
import jpen.PenProvider;
import jpen.PLevel;
import jpen.PLevelEvent;
import jpen.provider.AbstractPenDevice;
import jpen.PScroll;
import jpen.PScrollEvent;

class MouseDevice
	extends AbstractPenDevice {
	private final MouseAdapter myMouseAdapter=new MouseAdapter() {

		    @Override
		    public void mouseMoved(MouseEvent ev) {
			    scheduleMove(ev.getX(), ev.getY());
		    }

		    @Override
		    public void mouseDragged(MouseEvent ev) {
			    scheduleMove(ev.getX(), ev.getY());
		    }

		    @Override
		    public void mousePressed(MouseEvent ev) {
			    mouseButtonChanged(ev, true);
		    }

		    @Override
		    public void mouseReleased(MouseEvent ev) {
			    mouseButtonChanged(ev, false);
		    }

		    @Override
		    public void mouseWheelMoved(MouseWheelEvent ev) {
			    int value=ev.getWheelRotation();
			    PScroll.Type type=PScroll.Type.DOWN;
			    if(value<0) {
				    type=PScroll.Type.UP;
				    value=-value;
			    }
			    if(ev.getScrollType()==ev.WHEEL_UNIT_SCROLL && ev.getScrollAmount()>0) // > 0 : is because windows bug workaround, sometimes it is 0.
				    value*=ev.getScrollAmount();
			    getPen().scheduleScrollEvent(new PScroll(type.ordinal(), value));
		    }

	    };

	MouseDevice(PenProvider provider) {
		super(provider);
		setEnabled(true);
	}

	@Override
	public boolean isDigitizer() {
		return false;
	}

	@Override
	public String getName() {
		return "mouse";
	}

	@Override
	public void setEnabled(boolean enabled) {
		if(getEnabled()==enabled)
			return;
		if(getEnabled())
			unlisten();
		super.setEnabled(enabled);
		if(getEnabled())
			listen();
	}

	private void listen() {
		getComponent().addMouseListener(myMouseAdapter);
		getComponent().addMouseMotionListener(myMouseAdapter);
		getComponent().addMouseWheelListener(myMouseAdapter);
	}

	private void unlisten() {
		getComponent().removeMouseListener(myMouseAdapter);
		getComponent().removeMouseMotionListener(myMouseAdapter);
		getComponent().removeMouseWheelListener(myMouseAdapter);
	}

	private final PLevel[] changedLevelsA=new PLevel[2];
	private final List<PLevel> changedLevels=Arrays.asList(changedLevelsA);
	private void scheduleMove(int x, int y) {
		changedLevelsA[0]=new PLevel(PLevel.Type.X.ordinal(), x);
		changedLevelsA[1]=new PLevel(PLevel.Type.Y.ordinal(), y);
		getPen().scheduleLevelEvent(this, changedLevels);
	}

	private void mouseButtonChanged(MouseEvent ev, boolean state) {
		PButton.Type buttonType=getButtonType(ev.getButton());
		getPen().scheduleButtonEvent(new PButton(buttonType.ordinal(), state));
	}

	private static PButton.Type getButtonType(int buttonNumber) {
		switch(buttonNumber) {
		case MouseEvent.BUTTON1:
			return PButton.Type.LEFT;
		case MouseEvent.BUTTON2:
			return PButton.Type.CENTER;
		case MouseEvent.BUTTON3:
			return PButton.Type.RIGHT;
		}
		return PButton.Type.RIGHT;
	}
}