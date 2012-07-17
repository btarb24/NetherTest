package com.github.btarb24.NetherTest;

import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.PigZombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class EvtHandler implements Listener
{
	NetherTest _nether;
	public EvtHandler(NetherTest nether)
	{
		nether.getServer().getPluginManager().registerEvents(this, nether);
		_nether = nether; 
	}
	
	@EventHandler
	public void OnPlayerDeath(PlayerDeathEvent event)
	{
		//check if they were in the nether.  kill their session if they were. 
		//they will respawn in main world and not be able to get back in nether
		if (event.getEntity().getWorld().getName().equalsIgnoreCase(Configuration.NETHER_SERVER_NAME))
			_nether.endNetherSession(event.getEntity());
	}
	
	@EventHandler
	public void OnPlayerQuit(PlayerQuitEvent event)
	{
		//persists the player's used nether minutes if they logout while in nether
		if (event.getPlayer().getWorld().getName().equals(Configuration.NETHER_SERVER_NAME))
			_nether.logoutWhileInNether(event.getPlayer());
	}
	
	@EventHandler
	public void OnPlayerJoin(PlayerJoinEvent event)
	{
		//handling this event so that we can check if they're in the nether. If so then
		//teleport them to main world.  
		//positive effect.. checking info in nether will not display skewed stats
		//negative effect.. the player will not be in the same nether location as when they logged out
		if (event.getPlayer().getWorld().getName().equals(Configuration.NETHER_SERVER_NAME))
		{
			event.getPlayer().sendMessage(event.getPlayer().getWorld().getName());
			//punt them back to main world if they are out of time but somehow were still in nether.
			//odds of this are very low.  Teleporting them on join also causes an error and forces
			//them back to the server select screen.  Thankfully it's a super low chance situation
			//and that it's super easy to just double-click the server to log back in again.
			if (! _nether.hasTimeRemaining(event.getPlayer()))
			{
				event.getPlayer().sendMessage("teleport");
				event.getPlayer().teleport(Bukkit.getWorld("world").getSpawnLocation());
			}
		}
	}
	
	@EventHandler
	public void OnEntityDeath(EntityDeathEvent event)
	{
		//TODO: there has to be a better way of doing this.  Modifying the loot table after it has already been generated
		//is flawed.  I'd prefer to change the drop rates before the loot has been determined.
		if(event.getEntity() instanceof PigZombie)
		{
			//iterate over the loot that was determined by the core and remove any gold nuggets
			for(int i =0; i < event.getDrops().size(); i++)
			{
				//check if there is a nugget 
				if (event.getDrops().get(i).getType() == Material.GOLD_NUGGET)
				{
					//remove it  -- we'll add it back later if we're in our 10%
					event.getDrops().remove(i);
					break; //exit the for loop
				}
			}

			//get a random number 0-99		
			int rand = new Random().nextInt(100); 

			//give a nugget if it is within the configured drop percent
			if (rand < Configuration.PIGZOMBIE_GOLD_DROP_PERCENT) 
			{ //Give nugget  (yes, this will double up loot.. that's the downfall of not modifying the real loot table)
				event.getDrops().add(new ItemStack(Material.GOLD_NUGGET));
				_nether.getLogger().info(String.format("added nugget", rand));
			}
			
			//check if list is now empty  (this will happen if loot was 1 nugget and we removed it)
			if (event.getDrops().size() == 0)
				event.getDrops().add(new ItemStack(Material.ROTTEN_FLESH)); //at least give them this
		}
	}
}
