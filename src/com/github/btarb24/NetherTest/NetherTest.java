package com.github.btarb24.NetherTest;

import java.io.File;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.ListIterator;
import java.util.Timer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import com.github.btarb24.NetherTest.Configuration.Keys;
import com.github.btarb24.NetherTest.cmds.NetherCommand;


/* Transitions a player into the nether via player request.
 * Requests are only permissible once per XX hours. Players
 * are unable to bring in foreign items; though, they may 
 * return with whatever items they gather while inside the
 * nether world.
 */
public class NetherTest extends JavaPlugin 
{ 
	private DbAccess _dbAccess = null;
	private Timer _timer = new Timer("SessionMonitor");
	private SessionMonitorTask _monitorTask;
	private Configuration _config;
	
	public void onLoad()
	{
		//load the config file
		try {
			_config = new Configuration();
		} catch (Exception e) {
			getLogger().warning("Failed to load config file.  -- " + e.toString());
		}
		
		_dbAccess = new DbAccess(getLogger(), _config); //instantiate the db access class
		
		getLogger().info("NetherTest Loaded");
	}
	
	public void onEnable()
	{
		resetNetherWorld(false);
		
		new EvtHandler(this, _config); //instantiate the event handler class
				
		//start the session monitor
		_monitorTask = new SessionMonitorTask(getLogger(), _config);
		_timer.schedule(_monitorTask, 0, _config.getPropertyLong(Keys.MONITOR_INTERVAL)); 
		
		getCommand("nether").setExecutor(new NetherCommand(this, _dbAccess, _config));
		
		getLogger().info("NetherTest Enabled");
	}
		
	public void onDisable()
	{
		//stop the monitor and let it GC
		_monitorTask.cancel();
		_monitorTask = null;
		
		getLogger().info("NetherTest Disabled");
	}
	
	public void endNetherSession(Player player)
	{//Player needs their session minutes maxed out so that they can't join until time expires
		
		//max out their minutes in the db so they can't rejoin
		_dbAccess.endNetherSession(player);
		player.teleport(Bukkit.getWorld("world").getSpawnLocation()); //send them back to main world
		player.sendMessage(String.format("You died in the Nether. You may not re-enter for another %d hours.", _config.getPropertyInt(Keys.ENTRANCE_FREQUENCY)));
	}
	
	public void logoutWhileInNether(Player player)
	{
		//this will only persist their used minutes. It will not teleport them.. so they'll be in nether 
		//when they log back in later
		_dbAccess.exitNether(player);
	}
	
	public boolean hasTimeRemaining(Player player)
	{
		//verify if the player is currently permitted in nether (used when they login and were already in nether so no need to check inv)
		//the exceptionto this is that if they havent been online in a while and they're actually starting a new session then they get
		//to stay where they were and have their minutes set to 0.
		try {
			return _dbAccess.canEnter(player);
		} catch (SQLException e) {
			return false;  //just punt them back to main world if there was an error
		}
	}

	public void resetNetherWorld(boolean override)
	{
		//this is where the nether world file lives
		File worldFolder = new File(".\\" + _config.getProperty(Keys.NETHER_SERVER_NAME));
		
		//load from the config file so we know how many days the world shoud last
		int maxDays = _config.getPropertyInt(Keys.DAYS_UNTIL_NETHER_RESET);
		
		//check if world reset is disabled
		if (maxDays == 0)
			return;
		
		//get the current day from epoch.. that num is how many ms in a day
		Calendar now = Calendar.getInstance();
		long currentDay = now.getTimeInMillis() / 86400000; 
		
		//load the last reset day from the config file
		long lastResetDay = _config.getPropertyLong(Keys.LAST_RESET_DAY);
		
		//do we need to reset again?
		if (currentDay - lastResetDay >= maxDays || override)
		{
			//yes we do.. let's recursively delete the world folder and files
			deleteFolder(worldFolder);
			
			//and save the new day into the config file
			_config.setProperty(Keys.LAST_RESET_DAY, String.format("%d", currentDay));

			//did it work?
			getLogger().info(String.format("Nether world deletion successful: %b", worldFolder.exists()));
		}
	}
	
	private void deleteFolder(File file)
	{ //recursively delete folder and files. (lame java doesn't have this built in.. or at least that i could find).

		//sanity check
		if (file == null)
			return;
		
		//delete if we're down to a file
		if (file.isFile())
		{
			file.delete();
		}
		else
		{
			//iterate over children
			File[] files = file.listFiles();
			if (files != null && files.length > 0)
				for(int i = 0; i <files.length; i++)
					deleteFolder(files[i]);

			//delete this current folder now that the children have been dealt with
			file.delete();
		}
	}
	
	public boolean isInventoryValid(Player player)
	{
		//always allow admins
		if (player.hasPermission("Deity.nether.override"))
			return true;
		
		//get their inventory
		PlayerInventory inv = player.getInventory();
		
		//now iterate over it to ensure they ONLY have food, armor or tools.  no other items permitted
		ListIterator<ItemStack> iterator = inv.iterator();
		while (iterator.hasNext())
		{
			ItemStack cur = iterator.next();
			
			if (cur == null)
				continue;
			
			int id = cur.getTypeId();
			
			if (cur.getType().isEdible()) //MMmmmm..  food 
				continue;
			if (id >= 298 && id <= 317) //armor
				continue;
			if (id == 261 || id == 262) //bow & arrow
				continue;
			if ((id >= 267 && id <= 279) || (id>= 283 && id <= 286)) //tools .. minus hoes ..poor, lonely tools :(
				continue;
			if (id >= 290 && id <= 294) //there be dem hoes :D
				continue; 
			if (id == 359) //oh i guess shears can come too
				continue;
			
			//ok we found an item that's prohibited.  
			player.sendMessage("You can only bring tools, armor and food with you. Go store your other items and try again.");
			return false;
		}
		
		//good to go
		return true;
	}
	
	public Location getNetherSpawnLoc (World world)
	{
		//NOTE: nether world has a bedrock ceiling that's completely flat at Y127.  need to start below this to find usable land.
		
		int x = 0;
		int z = 0;
		int y = 100;
		Location loc = new Location(world, x, y, z);
		
		if (loc.getBlock().getType() == Material.AIR)
		{//good.. we're already in air.. just move down til we hit land.
			while (loc.getBlock().getType() == Material.AIR && y > 0)
				loc.setY(--y);
		}
		else
		{
			//ok so we started in something other than air.. just move down until we find some air.
			while (loc.getBlock().getType() != Material.AIR && y > 0)
				loc.setY(--y);
			
			//make sure we're above 0 and then move down until we go through all the air and hit land again
			if (y > 0)
				while (loc.getBlock().getType() == Material.AIR && y > 0)
					loc.setY(--y);
		}
		
		//ok, we have now either found land or we hit 0.. make sure it's not 0
		if (y == 0) 
			y = 60; //crap we never found a valid spot. just pick something in themiddle and we'll let them sort it out
		
		buildSpawnPlatform(world, loc);
		
		return loc;
	}
	
	private void buildSpawnPlatform(World world, Location loc)
	{
		// we need a 10x10 cobble platform with 10x10x10 air pocket above it.
		
		//first, make the platform
		for(int x = loc.getBlockX() - 5; x < loc.getBlockX() + 5; x++)
		{
			for(int z = loc.getBlockZ() - 5; z < loc.getBlockZ() + 5; z++)
			{
				world.getBlockAt(x, loc.getBlockY(), z).setType(Material.COBBLESTONE);
				
				//and now make sure there's an air cube above it
				for(int y = loc.getBlockY() +1; y < loc.getBlockY() +12; y++)
					world.getBlockAt(x, y, z).setType(Material.AIR);
			}
		}
	}
}
