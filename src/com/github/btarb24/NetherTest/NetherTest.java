package com.github.btarb24.NetherTest;

import java.sql.SQLException;
import java.util.ListIterator;
import java.util.Timer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

/* Transitions a player into the nether via player request.
 * Requests are only permissible once per XX hours. Players
 * are unable to bring in foreign items; though, they may 
 * return with whatever items they gather while inside the
 * nether world.
 */
public class NetherTest extends JavaPlugin 
{ 
	//how often a player can enter the nether. Measured in HOURS
	public static final int ENTRANCE_FREQUENCY = 12;
	
	//the max length of time a player may remain in the nether per session. measured in MINUTES
	public static final int MAX_SESSION_LENGTH = 60;
	
	private static final int MONITOR_INTERVAL = 3*60*1000; //3 minutes due to potentially high db access rate
	
	public static final String NETHER_SERVER_NAME = "world_nether";
	
	public static final int PIGZOMBIE_GOLD_DROP_PERCENT = 10; //what percent to have pigzombie drop a nugget
	
	private DbAccess _dbAccess = null;
	Timer _timer = new Timer("SessionMonitor");
	SessionMonitorTask _monitorTask;
	
	public void onLoad()
	{ 
		_dbAccess = new DbAccess(getLogger()); //instantiate the db access class
		getLogger().info("NetherTest Loaded");
	}
	
	public void onEnable()
	{
		new EvtHandler(this); //instantiate the event handler class
		
		//start the session monitor
		_monitorTask = new SessionMonitorTask(getLogger());
		_timer.schedule(_monitorTask, 0, MONITOR_INTERVAL); 
		
		getLogger().info("NetherTest Enabled");
	}
	 
	public void onDisable()
	{
		//stop the monitor and let it GC
		_monitorTask.cancel();
		_monitorTask = null;
		
		getLogger().info("NetherTest Disabled");
	}
	
	public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args)
	{	
		//ignore commands that do not begin with nether
		if (!cmd.getName().equalsIgnoreCase("nether"))
			return false;

		if (args.length == 0)
			return false; //return usage

		//the commands that have been coded are not valid for console access.. thus deny it
		Player player = null;
		if (sender instanceof Player)
			player = (Player) sender;
		else
			return true;
		
		//EVALUTE COMMANDS
		String command = args[0].toLowerCase();
		
		if(command.equals("enter"))
		{ //enter the nether world if permission is granted.
			//make sure they're not already in the nether. ignore them if they're dumb
			if (player.getWorld().getName().equals(NETHER_SERVER_NAME))
			{
				player.sendMessage("You're already in the Nether.");
				return true;
			}	
			
			try
			{//make sure they have time left and they have a valid inventory
				if (!isInventoryValid(player) || !_dbAccess.canEnter(player))
					return true; //access denied. message to player already sent
			}
			catch (SQLException e)
			{ //exception occurred. consider it a failed attempt.  try once more before giving up
				_dbAccess.initDbConnection(); //re-init the connection in case there is a problem
				try
				{
					if (! _dbAccess.canEnter(player))
						return true; //access denied. message to player already sent
				}
				catch (SQLException ex)
				{ //exception occurred again. Just display an error and give up
					player.sendMessage("An error occurred.  We cannot send you to the nether right now. Please wait and try again later.");
					
					getLogger().info(ex.getMessage());
					return true;
				}
			}
			
			//if we made it here then we can send them to the nether.
			player.teleport(getNetherSpawnLoc(Bukkit.getWorld(NETHER_SERVER_NAME)));
			return true;
		}
		else if(command.equals("exit"))
		{
			//make sure we're in the nether before porting/db modification
			if (player.getWorld().getName().equals(NETHER_SERVER_NAME))
			{
				player.teleport(Bukkit.getWorld("world").getSpawnLocation());
				_dbAccess.exitNether(player);
			}
			else
				player.sendMessage("You must be in the nether world to be able to exit it O.o");
			
			return true;
		}
		else if (command.equals("info"))
		{//output how much time they have left and how long to wait
			_dbAccess.outputInfo(player);
			return true;
		}
		else if (command.equals("cheat"))
		{//because i'm lazy and didn't want to walk to find something other than bedrock :)
			//and because i needed items to kill pig zombies to test 
			player.getInventory().addItem(new ItemStack(Material.DIAMOND_SWORD), new ItemStack(Material.DIAMOND_CHESTPLATE), new ItemStack(Material.DIAMOND_LEGGINGS), new ItemStack(Material.DIAMOND_BOOTS), new ItemStack(Material.DIAMOND_HELMET));
			if (player.getWorld().getName().equals(NETHER_SERVER_NAME))
				player.teleport(player.getWorld().getSpawnLocation());
			return true;
		}
		
		//default fall through to print out the usage
		return false; 
	}
	
	public void endNetherSession(Player player)
	{//Player needs their session minutes maxed out so that they can't join until time expires
		
		//max out their minutes in the db so they can't rejoin
		_dbAccess.EndNetherSession(player);
		player.teleport(Bukkit.getWorld("world").getSpawnLocation()); //send them back to main world
		player.sendMessage(String.format("You died in the Nether. You may not re-enter for another %d hours.", ENTRANCE_FREQUENCY ));
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
	
	private boolean isInventoryValid(Player player)
	{
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
	
	private Location getNetherSpawnLoc (World world)
	{
		//put you on 0,0 at the highest Y.
		int x = 0;
		int z = 0;
		int y = 250;
		Location loc = new Location(world, x, y, z);
		
		//gotta be a better way to do this.  I did some googling and got nowhere.. unsure how. I would have
		//though it would be very common to find a safe Y. My search terms likely sucked?  Anyway, not a pretty method, but functional
		//TODO: make this better
		while (loc.getBlock().getTypeId() == 0)
			loc.setY(--y);
		
		//make sure it wont kill you. change to a happy place if it will
		int block = loc.getBlockY();
		if (block == 10 || block == 11 || block == 51)
			loc.getBlock().setTypeId(91);
		
		// move back up 1 block
		loc.setY(y++);
		
		return loc;
	}
}
