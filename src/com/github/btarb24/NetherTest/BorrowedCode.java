package com.github.btarb24.NetherTest;

import java.io.File;
import java.io.RandomAccessFile;
import java.lang.ref.SoftReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.server.RegionFile;

import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

public class BorrowedCode {

	//This method was take from Bergerkiller : https://github.com/bergerkiller/MyWorlds/blob/master/src/com/bergerkiller/bukkit/mw/WorldManager.java
	//It was to bypass the problem of unloading a world leaving file locks: http://forums.bukkit.org/threads/deleting-world.47351/
	private static HashMap regionfiles;
	private static Field rafField;
	public static boolean clearWorldReference(World world, JavaPlugin plugin) {
		Field a = null;
		try {
			a = net.minecraft.server.RegionFileCache.class.getDeclaredField("a");
		} catch (NoSuchFieldException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (SecurityException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
    	a.setAccessible(true);
		try {
			regionfiles = (HashMap) a.get(null);
		} catch (IllegalArgumentException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IllegalAccessException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		try {
			rafField = net.minecraft.server.RegionFile.class.getDeclaredField("c");
		} catch (NoSuchFieldException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (SecurityException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		rafField.setAccessible(true);
		String worldname = Configuration.NETHER_SERVER_NAME;
		if (regionfiles == null) return false;
		if (rafField == null) return false;
		ArrayList<Object> removedKeys = new ArrayList<Object>();
		try {
			for (Object o : regionfiles.entrySet()) {
				Map.Entry e = (Map.Entry) o;
				File f = (File) e.getKey();
				if (f.toString().startsWith("." + File.separator + worldname)) {
					SoftReference ref = (SoftReference) e.getValue();
					try {
						RegionFile file = (RegionFile) ref.get();
						if (file != null) {
							RandomAccessFile raf = (RandomAccessFile) rafField.get(file);
							raf.close();
							removedKeys.add(f);
						}
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}
			}
		} catch (Exception ex) {
			plugin.getLogger().warning("Exception while removing world reference for '" + worldname + "'!");
		}
		for (Object key : removedKeys) {
			regionfiles.remove(key);
		}
		return true;
	}
	 
}
