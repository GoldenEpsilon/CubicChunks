/*******************************************************************************
 * Copyright (c) 2014 Nick Whitney.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *     Nick Whitney - initial implementation and adaptation from libnoise.
 ******************************************************************************/
package cuchaz.cubicChunks.generator.builder;

import libnoiseforjava.module.Clamp;
import libnoiseforjava.module.ModuleBase;
import libnoiseforjava.module.Perlin;
import libnoiseforjava.module.ScaleBias;
import libnoiseforjava.module.ScalePoint;

public class BasicBuilder implements IBuilder
{
	protected ModuleBase finalModule;

	// Planet seed.  Change this to generate a different planet.
	int SEED = 0;

	// Maximum elevation, in meters.  This value is approximate.
	double MAX_ELEV = 8192.0;

	// Specifies the sea level.  This value must be between -1.0
	// (minimum elevation) and +1.0 (maximum planet elevation.)
	double SEA_LEVEL = 0.0;

	int NUM_OCTAVES = 10;

	double SCALE_X = 1;
	double SCALE_Y = 1;
	double SCALE_Z = 1;

	double persistance = 0.5;
	double clampMin, clampMax;
	double lacunarity = Math.E;//2.718..., just in case somebody don't know what is the value of e

	@Override
	public void setSeed( int seed )
	{
		this.SEED = seed;
	}

	public void setMaxElev( double maxElev )
	{
		this.MAX_ELEV = maxElev;
		clampMin = -MAX_ELEV + SEA_LEVEL;
		clampMax = MAX_ELEV + SEA_LEVEL;
	}

	@Override
	public void setSeaLevel( double seaLevel )
	{
		this.SEA_LEVEL = seaLevel;
		clampMin = -MAX_ELEV + SEA_LEVEL;
		clampMax = MAX_ELEV + SEA_LEVEL;
	}

	public void setOctaves( int numOctaves )
	{
		this.NUM_OCTAVES = numOctaves;
	}

	public void setFreq( double scale )
	{
		this.SCALE_X = this.SCALE_Y = this.SCALE_Z = scale;
	}

	public void setFreq( double scaleX, double scaleY, double scaleZ )
	{
		this.SCALE_X = scaleX;
		this.SCALE_Y = scaleY;
		this.SCALE_Z = scaleZ;
	}

	public void setPersistance( double p )
	{
		this.persistance = p;
	}

	public void setlacunarity( double l )
	{
		this.lacunarity = l;
	}
	
	public void setClamp(double min, double max){
		this.clampMin = min;
		this.clampMax = max;
	}

	@Override
	public void build()
	{
		Perlin perlin = new Perlin();
		perlin.setSeed( SEED );
		perlin.setFrequency( 1.0 );
		perlin.setPersistence( persistance );
		perlin.setLacunarity( lacunarity );
		perlin.setOctaveCount( NUM_OCTAVES );
		perlin.build();

		ScaleBias scaleBias = new ScaleBias( perlin );
		scaleBias.setScale( MAX_ELEV );
		scaleBias.setBias( SEA_LEVEL );

		ScalePoint scalePoint = new ScalePoint( scaleBias );
		scalePoint.setScale( SCALE_X, SCALE_Y, SCALE_Z );

		Clamp clamp = new Clamp( scalePoint );
		clamp.setBounds( clampMin, clampMax );

		finalModule = clamp;
	}

	@Override
	public double getValue( double x, double y, double z )
	{
		return finalModule.getValue( x, y, z );
	}
}
