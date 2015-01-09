/*******************************************************************************
 * This file is part of Cubic Chunks, licensed under the MIT License (MIT).
 *
 * Copyright (c) Tall Worlds
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *******************************************************************************/
package cubicchunks.server;

import java.util.List;
import java.util.Random;
import java.util.Set;

import net.minecraft.entity.EnumCreatureType;
import net.minecraft.profiler.Profiler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WeightedRandom;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.ISaveHandler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cubicchunks.CubeProvider;
import cubicchunks.CubeProviderTools;
import cubicchunks.CubeWorld;
import cubicchunks.CubeWorldProvider;
import cubicchunks.accessors.WorldServerAccessor;
import cubicchunks.generator.GeneratorPipeline;
import cubicchunks.generator.biome.WorldColumnManager;
import cubicchunks.lighting.LightingManager;
import cubicchunks.util.AddressTools;
import cubicchunks.util.Coords;
import cubicchunks.world.Column;
import cuchaz.magicMojoModLoader.util.Util;

public class CubeWorldServer extends WorldServer implements CubeWorld
{
	private static final Logger log = LogManager.getLogger();
	
	private LightingManager m_lightingManager;
	private GeneratorPipeline m_generatorPipeline;
	
	public CubeWorldServer( MinecraftServer server, ISaveHandler saveHandler, String worldName, int dimension, WorldSettings settings, Profiler profiler )
	{
		super( server, saveHandler, worldName, dimension, settings, profiler );
		
		// set the player manager
		CubePlayerManager playerManager = new CubePlayerManager( this, server.getConfigurationManager().getViewDistance() );
		WorldServerAccessor.setPlayerManager( this, playerManager );
	}
	
	@Override
	protected IChunkProvider createChunkProvider()
    {
		// create the cube provider
		CubeProviderServer provider = new CubeProviderServer( this );
		
		// tell World and WorldServer about it
		chunkProvider = provider;
		WorldServerAccessor.setChunkProvider( this, provider );
		
		// init systems dependent on cubes
		m_lightingManager = new LightingManager( this, provider );
		m_generatorPipeline = getCubeWorldProvider().createGeneratorPipeline( this );
		
		return chunkProvider;
    }
	
	@Override
	public CubeProviderServer getCubeProvider( )
	{
		return (CubeProviderServer)chunkProvider;
	}
	
	public CubeWorldProvider getCubeWorldProvider( )
	{
		return (CubeWorldProvider)provider;
	}
	
	@Override
	public LightingManager getLightingManager( )
	{
		return m_lightingManager;
	}
	
	public GeneratorPipeline getGeneratorPipeline( )
	{
		return m_generatorPipeline;
	}
	
	@Override
	public void tick( )
	{
		super.tick();
		
		theProfiler.startSection( "generatorPipeline" );
		m_generatorPipeline.tick();
		theProfiler.endSection();
		
		theProfiler.startSection( "lightingEngine" );
		m_lightingManager.tick();
		theProfiler.endSection();
		
	}
	
    /**
     * only spawns creatures allowed by the chunkProvider
     */
	@Override
    public net.minecraft.world.biome.BiomeGenBase.SpawnListEntry spawnRandomCreature(EnumCreatureType par1EnumCreatureType, int par2, int par3, int par4)
    {
        List var5 = (List) this.getChunkProvider().getPossibleCreatures(par1EnumCreatureType, par2, par3, par4);
        return var5 != null && !var5.isEmpty() ? (net.minecraft.world.biome.BiomeGenBase.SpawnListEntry)WeightedRandom.getRandomItem(this.rand, var5) : null;
//		return null;
    }
	
	public long getSpawnPointCubeAddress( )
	{
		return AddressTools.getAddress(
			Coords.blockToCube( worldInfo.getSpawnX() ),
			Coords.blockToCube( worldInfo.getSpawnY() ),
			Coords.blockToCube( worldInfo.getSpawnZ() )
		);
	}
	
	@Override
	public boolean checkChunksExist( int minBlockX, int minBlockY, int minBlockZ, int maxBlockX, int maxBlockY, int maxBlockZ )
	{
		return CubeProviderTools.blocksExist( (CubeProvider)chunkProvider, minBlockX, minBlockY, minBlockZ, maxBlockX, maxBlockY, maxBlockZ );
	}
	
	@Override
	public boolean updateLightByType( EnumSkyBlock lightType, int blockX, int blockY, int blockZ )
    {
		// forward to the new lighting system
		return m_lightingManager.computeDiffuseLighting( blockX, blockY, blockZ, lightType );
    }
	
	@Override //            tick
	@SuppressWarnings( "unchecked" )
	protected void func_147456_g( )
	{
		super.func_147456_g();
		
		// apply random ticks
		for( ChunkCoordIntPair coords : (Set<ChunkCoordIntPair>)activeChunkSet )
		{
			Column column = (Column)chunkProvider.provideChunk( coords.chunkXPos, coords.chunkZPos );
			column.doRandomTicks();
		}
	}
	
	@Override
	protected void createSpawnPosition( WorldSettings worldSettings )
	{
		// NOTE: this is called inside the world constructor
		// this is apparently called before the world is generated
		// we'll have to do our own generation to find the spawn point
		
		if( !provider.canRespawnHere() )
		{
			worldInfo.setSpawnPosition( 0, 0, 0 );
			return;
		}
		
		// pick a default fail-safe spawn point
		int spawnBlockX = 0;
		int spawnBlockY = provider.getAverageGroundLevel();
		int spawnBlockZ = 0;
		
		Random rand = new Random( getSeed() );
		
		// defer to the column manager to find the x,z part of the spawn point
		WorldColumnManager columnManager = getCubeWorldProvider().getWorldColumnMananger();
		ChunkPosition spawnPosition = columnManager.func_150795_a( 0, 0, 256, columnManager.getBiomesToSpawnIn(), rand );
		if( spawnPosition != null )
		{
			spawnBlockX = spawnPosition.field_151329_a;
			spawnBlockZ = spawnPosition.field_151328_c;
		}
		else
		{
			log.warn( "Unable to find spawn biome" );
		}
		
		log.info( "Searching for suitable spawn point..." );
		
		// generate some world around the spawn x,z at sea level
		int spawnCubeX = Coords.blockToCube( spawnBlockX );
		int spawnCubeY = Coords.blockToCube( getCubeWorldProvider().getSeaLevel() );
		int spawnCubeZ = Coords.blockToCube( spawnBlockZ );
		final int SearchDistance = 4;
		CubeProviderServer cubeProvider = getCubeProvider();
		for( int cubeX=spawnCubeX-SearchDistance; cubeX<=spawnCubeX+SearchDistance; cubeX++ )
		{
			for( int cubeY=spawnCubeY-SearchDistance; cubeY<=spawnCubeY+SearchDistance; cubeY++ )
			{
				for( int cubeZ=spawnCubeZ-SearchDistance; cubeZ<=spawnCubeZ+SearchDistance; cubeZ++ )
				{
					cubeProvider.loadCube( cubeX, cubeY, cubeZ );
				}
			}
		}
		getGeneratorPipeline().generateAll();
		
		// make some effort to find a suitable spawn point, but don't guarantee it
		for( int i=0; i<1000 && !provider.canCoordinateBeSpawn( spawnBlockX, spawnBlockZ ); i++ )
		{
			spawnBlockX += Util.randRange( rand, -16, 16 );
			spawnBlockZ += Util.randRange( rand, -16, 16 );
		}
		
		// save the spawn point
		worldInfo.setSpawnPosition( spawnBlockX, spawnBlockY, spawnBlockZ );
		log.info( String.format( "Found spawn point at (%d,%d,%d)", spawnBlockX, spawnBlockY, spawnBlockZ ) );
		
		if( worldSettings.isBonusChestEnabled() )
		{
			createBonusChest();
		}
	}
}
