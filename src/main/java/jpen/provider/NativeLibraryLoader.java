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
package jpen.provider;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.logging.Logger;
import java.util.Map;
import java.util.prefs.Preferences;
import jpen.provider.Utils;

public class NativeLibraryLoader{
	private static final Logger L=Logger.getLogger(NativeLibraryLoader.class.getName());
	{
		//L.setLevel(Level.ALL);
	}
	private static String PREFERENCE_KEY$ARCHITECTURE="NativeLibraryLoader.architecture";

	private final Map<String, Collection<String>> dataModelToArchitectures=new HashMap<String, Collection<String>>();
	private boolean loaded;

	public NativeLibraryLoader(){
		this(new String[]{""});
	}

	public NativeLibraryLoader(String[] architectures){
		this(architectures, architectures);
	}

	public NativeLibraryLoader(String[] architectures32, String[] architectures64){
		dataModelToArchitectures.put("32", Arrays.asList(architectures32));
		dataModelToArchitectures.put("64", Arrays.asList(architectures64));
	}

	public synchronized void load(){
		if(!loaded){
			String preferredArchitecture=getPreferredArchitecture();
			Throwable loadExceptionCause=null;
			if(preferredArchitecture!=null)
				try{
					Utils.loadLibrary(preferredArchitecture);
				}catch(Throwable t){
					setPreferredArchitecture(null);
					loadExceptionCause=t;
				}
			else{
				String dataModel=getJavaVMDataModel();
				Collection<String> architectures=dataModelToArchitectures.get(dataModel);
				if(architectures==null)
					throw new IllegalStateException("Unsupported data model: "+dataModel);
				for(String architecture: architectures){
					try{
						L.info("trying to load architecture: \""+architecture+"\"");
						Utils.loadLibrary(architecture);
						setPreferredArchitecture(architecture);
						loadExceptionCause=null;
						break;
					}catch(Throwable t){
						setPreferredArchitecture(null);
						loadExceptionCause=t;
					}
				}
			}
			loaded=true;
			if(loadExceptionCause!=null)
				throw new LoadException(loadExceptionCause);
		}
	}

	public static class LoadException
		extends RuntimeException{
		LoadException(Throwable cause){
			super(cause);
		}
	}

	private static String getJavaVMDataModel(){
		String dataModel=AccessController.doPrivileged(new PrivilegedAction<String>() {
			    //@Override
			    public String run() {
				    return System.getProperty("sun.arch.data.model");
			    }
		    });
		return dataModel==null? "32": dataModel;
	}

	private String getPreferredArchitecture(){
		return AccessController.doPrivileged(new PrivilegedAction<String>(){
			       //@Override
			       public String run(){
				       String override=System.getProperty("jpen.provider.architecture");
				       if(override!=null)
					       return override;
				       Preferences preferences=Preferences.userNodeForPackage(NativeLibraryLoader.class);
				       return preferences.get(PREFERENCE_KEY$ARCHITECTURE, null);
			       }
		       });
	}

	private static void setPreferredArchitecture(final String architecture){
		AccessController.doPrivileged(new PrivilegedAction<Object>(){
			    //@Override
			    public String run(){
				    Preferences preferences=Preferences.userNodeForPackage(NativeLibraryLoader.class);
				    if(architecture==null){
					    preferences.remove(PREFERENCE_KEY$ARCHITECTURE);
						}
				    else{
							L.info("setting preferred architecture value to: \""+architecture+"\"");
					    preferences.put(PREFERENCE_KEY$ARCHITECTURE, architecture);
						}
				    return null;
			    }
		    });
	}
}