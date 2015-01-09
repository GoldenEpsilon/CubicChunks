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
package cuchaz.cubicChunks.generator.biome.alternateGen;

import cuchaz.cubicChunks.cache.CacheMap;
import cuchaz.cubicChunks.generator.biome.BiomeCache;
import cuchaz.cubicChunks.generator.biome.WorldColumnManager;
import cuchaz.cubicChunks.generator.biome.biomegen.CubeBiomeGenBase;
import cuchaz.cubicChunks.generator.builder.BasicBuilder;
import cuchaz.cubicChunks.generator.builder.IBuilder;
import static cuchaz.cubicChunks.generator.terrain.GlobalGeneratorConfig.maxElev;
import cuchaz.cubicChunks.server.CubeWorldServer;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import net.minecraft.util.MathHelper;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.biome.BiomeGenBase;

public class AlternateWorldColumnManager extends WorldColumnManager
{
	//Cache for noise fields.
	private final CacheMap<Long, NoiseArrays> noiseCache = new CacheMap<Long, NoiseArrays>( 1024, 1100 );
	

	//Builders for height volatility temperature and rainfall
	private final BasicBuilder volatilityBuilder, heightBuilder, tempBuilder, rainfallBuilder;
	private final BiomeCache biomeCache;
	private final List<CubeBiomeGenBase> biomesToSpawnIn;

	private final List<BiomeFinder> biomeFindersPriorityList;
	
	protected Long worldSeed;

	protected static Long xzToLong( int x, int z )
	{
		return (x & 0xFFFFFFFFl) | ((z & 0xFFFFFFFFl) << 32);
	}

	private AlternateBiomeGen biomeGen;
	
	public AlternateWorldColumnManager( CubeWorldServer world )
	{
		this.worldSeed = world.getSeed();
		this.biomeGen = new AlternateBiomeGen( world );
		this.biomeCache = new BiomeCache( this );
		this.biomesToSpawnIn = new ArrayList<CubeBiomeGenBase>( 7 );
		this.biomesToSpawnIn.add( CubeBiomeGenBase.forest );
		this.biomesToSpawnIn.add( CubeBiomeGenBase.plains );
		this.biomesToSpawnIn.add( CubeBiomeGenBase.taiga );
		this.biomesToSpawnIn.add( CubeBiomeGenBase.taigaHills );
		this.biomesToSpawnIn.add( CubeBiomeGenBase.forestHills );
		this.biomesToSpawnIn.add( CubeBiomeGenBase.jungle );
		this.biomesToSpawnIn.add( CubeBiomeGenBase.jungleHills );

		//this.world = world;
		double freqH = 0.003 / (maxElev / 256) / (4 * Math.PI);
		double freqV = 0.003 / (maxElev / 256) / (4 * Math.PI);
		double freqT = 0.005 / (maxElev / 256) / (4 * Math.PI);
		double freqR = 0.005 / (maxElev / 256) / (4 * Math.PI);

		int octaves = (int)(Math.log( maxElev ));

		Random rand = new Random( world.getSeed() );
		rand.setSeed( rand.nextLong() ^ rand.nextLong() );

		heightBuilder = new BasicBuilder();
		heightBuilder.setSeed( rand.nextInt() );
		heightBuilder.setOctaves( octaves  );
		heightBuilder.setMaxElev( 1.3 );
		heightBuilder.setFreq( freqH );
		heightBuilder.build();

		volatilityBuilder = new BasicBuilder();
		volatilityBuilder.setSeed( rand.nextInt() );
		volatilityBuilder.setOctaves( octaves );
		volatilityBuilder.setMaxElev( 1 );
		volatilityBuilder.setClamp( -0.5, 0.5 );
		volatilityBuilder.setFreq( freqV );
		volatilityBuilder.build();

		tempBuilder = new BasicBuilder();
		tempBuilder.setSeed( rand.nextInt() );
		tempBuilder.setOctaves( octaves );
		tempBuilder.setMaxElev( 0.9);
		tempBuilder.setSeaLevel( 0.5 );
		tempBuilder.setClamp( 0, 1 );
		tempBuilder.setFreq( freqT );
		tempBuilder.build();

		rainfallBuilder = new BasicBuilder();
		rainfallBuilder.setSeed( rand.nextInt() );
		rainfallBuilder.setOctaves( octaves  );
		rainfallBuilder.setMaxElev( 0.9 );
		rainfallBuilder.setSeaLevel( 0.5 );
		rainfallBuilder.setClamp( 0, 1 );
		rainfallBuilder.setFreq( freqR );
		rainfallBuilder.build();


		this.biomeFindersPriorityList = new LinkedList<BiomeFinder>();

		int flags = 0;//default biome finder. Check everything
		this.biomeFindersPriorityList.add( new BiomeFinder( world, biomeGen, flags ) );

		flags |= BiomeFinder.IGNORE_TEMP | BiomeFinder.IGNORE_RAINFALL | BiomeFinder.TEMP_INV | BiomeFinder.RAINFALL_INV;//if no biome found - ignore values player can't see and try again
		this.biomeFindersPriorityList.add( new BiomeFinder( world, biomeGen, flags ) );

		flags |= BiomeFinder.FORCE_NO_EXTENDED_HEIGHT_VOL_CHEKCS;//maybe try without extended height and volatility checks...
		this.biomeFindersPriorityList.add( new BiomeFinder( world, biomeGen, flags ) );

		flags |= BiomeFinder.IGNORE_VOL | BiomeFinder.VOLATILITY_INV | BiomeFinder.NO_RARITY;//If averything else fails - ignore volatility to find something. Ignore rarity.
		this.biomeFindersPriorityList.add( new BiomeFinder( world, biomeGen, flags ) );
		//If still nothing found - give up and throw an Exception...
		//ignoring height would be too risky...
	}

	@Override
	@Deprecated
	public float[] getRainfall( float[] downfall, int blockX, int blockZ, int width, int length )
	{
		assert width <= 17 && length <= 17;
		
		double[][] rain = getRainfallArray( blockX >> 4, blockZ >> 4 );
		if( downfall == null || downfall.length != width * length )
		{
			downfall = new float[width * length];
		}
		for( int x = 0; x < width; x++ )
		{
			for( int z = 0; z < length; z++ )
			{
				downfall[z * length + x] = (float)rain[x][z];
			}
		}
		return downfall;
	}

	/**
	 * @param columnX
	 * @param columnZ
	 * @return Temperature array
	 */
	public double[][] getTempArray( int columnX, int columnZ )
	{
		return getFromCacheOrGenerate( noiseCache, NoiseArrays.Type.TEMPERATURE, columnX, columnZ );
	}

	/**
	 * @param columnX
	 * @param columnZ
	 * @return Rainfall array
	 */
	public double[][] getRainfallArray( int columnX, int columnZ )
	{
		return getFromCacheOrGenerate( noiseCache, NoiseArrays.Type.RAINFALL, columnX, columnZ );
	}

	/**
	 * @param columnX
	 * @param columnZ
	 * @return Volatility array
	 */
	public double[][] getVolArray( int columnX, int columnZ )
	{
		return getFromCacheOrGenerate( noiseCache, NoiseArrays.Type.VOLATILITY, columnX, columnZ );
	}

	/**
	 * @param columnX
	 * @param columnZ
	 * @return Height array, needs to be interpolated (4x4 by default)
	 */
	public double[][] getHeightArray( int columnX, int columnZ )
	{
		return getFromCacheOrGenerate( noiseCache, NoiseArrays.Type.HEIGHT, columnX, columnZ );
	}
	
	
	public double getHeight( int x, int z )
	{
		return heightBuilder.getValue( x, 0, z );
	}

	public double getVolatility( int x, int z )
	{
		return volatilityBuilder.getValue( x, 0, z );
	}

	public double getTemp( int x, int z )
	{
		return tempBuilder.getValue( x, 0, z );
	}

	public double getRainfall( int x, int z )
	{
		return rainfallBuilder.getValue( x, 0, z );
	}

	@Override
	public BiomeGenBase[] getBiomeGenAt( BiomeGenBase[] biomes, int blockX, int blockZ, int width, int length, boolean fromCache )
	{
		//IntCache.resetIntCache();

		if( biomes == null || biomes.length < width * length )
		{
			biomes = new CubeBiomeGenBase[width * length];
		}

		if( fromCache && width == 16 && length == 16 && (blockX & 15) == 0 && (blockZ & 15) == 0 )
		{
			CubeBiomeGenBase[] cachedBiomes = this.biomeCache.getCachedBiomes( blockX, blockZ );
			System.arraycopy( cachedBiomes, 0, biomes, 0, width * length );
			return biomes;
		}
		else
		{
			this.generateBiomes( biomes, blockX, blockZ, width, length );

			return biomes;
		}
	}

	@Override
	public BiomeGenBase[] getBiomesForGeneration( BiomeGenBase[] biomes, int xGenBlock, int zGenBlock, int width, int length )
	{
		//this is unuses=d
		throw new UnsupportedOperationException( "AlternateWrldColumnManager.getBiomesForGeneration()" );
	}

	@SuppressWarnings("rawtypes")
	@Override
	public boolean areBiomesViable( int x, int par2, int par3, List list )
	{
		//This is probably unused. No crash so far...
		throw new UnsupportedOperationException( "AlternateWrldColumnManager.areBiomesViable()" );
	}

	@Override
	@SuppressWarnings("rawtypes") // sampleBlockXZFromSpawnableBiome
	public ChunkPosition func_150795_a( int blockX, int blockZ, int blockDistance, List spawnBiomes, Random rand )
	{
		//TODO: getBlockXZFromSpawnableBiome
		return new ChunkPosition( 0, 0, 0 );
		/*// looks like we sample one point of noise for every 4 blocks
		 final int BlocksPerSample = 4;
		
		 // sample the noise to get biome data
		 IntCache.resetIntCache();
		 int minNoiseX = ( blockX - blockDistance )/BlocksPerSample;
		 int minNoiseZ = ( blockZ - blockDistance )/BlocksPerSample;
		 int maxNoiseX = ( blockX + blockDistance )/BlocksPerSample;
		 int maxNoiseZ = ( blockZ + blockDistance )/BlocksPerSample;
		 int noiseXSize = maxNoiseX - minNoiseX + 1;
		 int noiseZSize = maxNoiseZ - minNoiseZ + 1;
		 int[] biomeNoise = genBiomes.getInts( minNoiseX, minNoiseZ, noiseXSize, noiseZSize );
		
		 // collect all xz positions from spawnable biomes
		 List<ChunkPosition> possibleSpawns = new ArrayList<ChunkPosition>();
		 for( int x=0; x<noiseXSize; x++ )
		 {
		 for( int z=0; z<noiseZSize; z++ )
		 {
		 CubeBiomeGenBase biome = CubeBiomeGenBase.getBiome( biomeNoise[x + z*noiseXSize] );
		 if( spawnBiomes.contains( biome ) )
		 {
		 int spawnBlockX = ( minNoiseX + x )*BlocksPerSample;
		 int spawnBlockZ = ( minNoiseZ + z )*BlocksPerSample;
		 possibleSpawns.add( new ChunkPosition( spawnBlockX, 0, spawnBlockZ ) );
		 }
		 }
		 }
		
		 if( possibleSpawns.isEmpty() )
		 {
		 return null;
		 }
		
		 // pick a random spawn from the list
		 return possibleSpawns.get( rand.nextInt( possibleSpawns.size() ) );*/
	}

	@Override
	public void cleanupCache()
	{
		//For noise arrays done automatically by Java GC
		biomeCache.cleanupCache();
	}

	public double getRealVolatility( double volatilityRaw, double heightRaw, double rainfallRaw, double temperatureRaw )
	{
		return Math.min( Math.abs( heightRaw ), (Math.abs( volatilityRaw ) * 0.95D + 0.05) * Math.sqrt( Math.abs( heightRaw * heightRaw ) ) ) * (1 - Math.pow( 1 - rainfallRaw * temperatureRaw, 4 ));
	}

	public double getRealHeight( double heightRaw )
	{
		//return (heightRaw * 1.4 + heightRaw*heightRaw*Math.sin(heightRaw*17)) * 30;
		//Maybe use this function?: ((sin(x*pi - pi/2)^3)/2+0.5)^1.3
		if (heightRaw < -0.0){
			return heightRaw;//ocean
		}
		else if (heightRaw > 0.0 && heightRaw <= 0.05){
			return heightRaw * 0.2D;//beach, swamp
		}
		else if (heightRaw > 0.05 && heightRaw <= 0.3){
			return heightRaw * 0.5D - 0.015D;//plains, savanna, etc.
		}
		else if (heightRaw > 0.3 && heightRaw <= 0.6){
			return heightRaw - 0.165D;//forest, desert, mesa, etc.
		}
		else if (heightRaw > 0.6 && heightRaw <= 0.8){
			return 1.5D * heightRaw - 0.465D;//hill biomes
		}
		return 1.75 * heightRaw - 0.065D - 0.6;//extreme hills, ice mountains
	}

	private double[][] populateArray( double[][] array, IBuilder builder, int startX, int startZ, int xSize, int zSize )
	{
		if( array == null || array.length != xSize || array[0].length != zSize )
		{
			throw new IllegalArgumentException();
		}
		for( int x = 0; x < xSize; x++ )
		{
			for( int z = 0; z < zSize; z++ )
			{
				array[x][z] = builder.getValue( startX + x, 0, startZ + z );
			}
		}
		return array;
	}

	private double[][] getFromCacheOrGenerate( CacheMap<Long, NoiseArrays> cache, NoiseArrays.Type type, int columnX, int columnZ )
	{
		NoiseArrays arrays = cache.get( xzToLong( columnX, columnZ ) );
		if( arrays == null )
		{
			generateAllNoiseArrays( columnX, columnZ );
			arrays = cache.get( xzToLong( columnX, columnZ ) );
			assert arrays != null;
		}
		return arrays.get( type );
	}

	private void generateAllNoiseArrays( int columnX, int columnZ )
	{
		//yes, length 17. This is correct.
		double[][] volArray = new double[17][17];
		double[][] heightArray = new double[17][17];
		double[][] tempArray = new double[17][17];
		double[][] rainfallArray = new double[17][17];

		//and multimply position by 16, not 17. This is correct too. In AlternateTerrainProcessor we need values at 0, 4, 8, 12 and 16. Value at 16 is 17-th array element.
		populateArray( volArray, volatilityBuilder, columnX * 16, columnZ * 16, 17, 17 );
		populateArray( heightArray, heightBuilder, columnX * 16, columnZ * 16, 17, 17 );
		populateArray( tempArray, tempBuilder, columnX * 16, columnZ * 16, 17, 17 );
		populateArray( rainfallArray, rainfallBuilder, columnX * 16, columnZ * 16, 17, 17 );
		
		NoiseArrays arrays = new NoiseArrays( volArray, heightArray, tempArray, rainfallArray );
		noiseCache.put( xzToLong( columnX, columnZ ), arrays );
	}

	protected void generateBiomes( BiomeGenBase[] biomes, int blockX, int blockZ, int width, int length )
	{
		int minChunkX = blockX >> 4;
		int minChunkZ = blockZ >> 4;
		int maxChunkX = (blockX + width - 1) >> 4;
		int maxChunkZ = (blockZ + length - 1) >> 4;

		assert minChunkX <= maxChunkX;
		assert minChunkZ <= maxChunkZ;

		for( int x = minChunkX; x <= maxChunkX; x++ )
		{
			for( int z = minChunkZ; z <= maxChunkZ; z++ )
			{
				double[][] heightArray = this.getHeightArray(x, z);
				double[][] volArray = getVolArray( x, z );
				double[][] tempArray = getTempArray( x, z );
				double[][] rainfallArray = getRainfallArray( x, z );
				for( int xRel = 0; xRel < 16; xRel++ )
				{
					for( int zRel = 0; zRel < 16; zRel++ )
					{
						double vol = volArray[xRel][zRel];
						double height = heightArray[xRel][zRel];
						double temp = tempArray[xRel][zRel];
						double rainfall = rainfallArray[xRel][zRel];
						
						height = getRealHeight(height);
						height = MathHelper.clamp_double(height, -1, 1);
						vol = getRealVolatility( vol / 2, height, rainfall, temp );
						CubeBiomeGenBase biome = getBiomeForValues( x, z, vol, height, temp, rainfall );

						biomes[zRel * length + xRel] = biome;
					}
				}
			}
		}
	}

	protected CubeBiomeGenBase getBiomeForValues( double x, double z, double vol, double height, double temp, double rainfall )
	{
		CubeBiomeGenBase biome = null;
		for( BiomeFinder finder: biomeFindersPriorityList )
		{
			biome = finder.getBiomeForValues( x, z, vol, height, temp, rainfall );
			if( biome != null )
			{
				break;
			}
		}

		if( biome == null )
		{
			throw new RuntimeException( String.format( "No biome for values found: h = %f, v = %f, t = %f, r = %f", height, vol, temp, rainfall ) );
		}

		return biome;
	}
	
	
}