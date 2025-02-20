/*
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2007 - 2021 Fiji developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
/**
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License 2
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * An execption is the FFT implementation of Dave Hale which we use as a library,
 * wich is released under the terms of the Common Public License - v1.0, which is 
 * available at http://www.eclipse.org/legal/cpl-v10.html  
 *
 * @author Stephan Preibisch
 */

import static stitching.CommonFunctions.LIN_BLEND;
import static stitching.CommonFunctions.addHyperLinkListener;
import static stitching.CommonFunctions.methodListCollection;
import static stitching.CommonFunctions.rgbTypes;
import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.gui.MultiLineLabel;
import ij.plugin.PlugIn;

import java.util.ArrayList;

import loci.common.services.ServiceFactory;
import loci.formats.ChannelSeparator;
import loci.formats.IFormatReader;
import loci.formats.meta.IMetadata;
import loci.formats.meta.MetadataRetrieve;
import loci.formats.services.OMEXMLService;
import ome.units.quantity.Length;
import stitching.CommonFunctions;
import stitching.GridLayout;
import stitching.ImageInformation;
import stitching.model.TranslationModel2D;
import stitching.model.TranslationModel3D;
import stitching.utils.Log;

/**
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */

public class Stitch_Multiple_Series_File implements PlugIn
{
	final private String myURL = "http://fly.mpi-cbg.de/~preibisch/contact.html";

	public double alpha, thresholdR, thresholdDisplacementRelative, thresholdDisplacementAbsolute;

	public static String fileNameStatic = "";
	public static boolean computeOverlapStatic = true;
	public static double overlapStatic = 10;
	public static boolean invertXStatic = false, invertYStatic = false;
	public static boolean ignoreZStageStatic = false;

	public static String fusionMethodStatic = methodListCollection[LIN_BLEND];
	public static double alphaStatic = 1.5;
	public static double thresholdRStatic = 0.3;
	public static double thresholdDisplacementRelativeStatic = 2.5;
	public static double thresholdDisplacementAbsoluteStatic = 3.5;
	public static boolean previewOnlyStatic = false;
	public static boolean ignoreCalibrationStatic = false;
	
	@Override
	public void run(String arg0)
	{
		GenericDialogPlus gd = new GenericDialogPlus( "Stitch Multiple Series File" );
		
		gd.addFileField( "File", fileNameStatic, 50 );		
		gd.addCheckbox( "Compute_overlap (or trust coordinates in the file)", computeOverlapStatic );
		gd.addCheckbox( "Ignore_Calibration", ignoreCalibrationStatic );
		gd.addSlider( "Increase_overlap [%]", 0, 100, overlapStatic );
		gd.addCheckbox( "Invert_X coordinates", invertXStatic );
		gd.addCheckbox( "Invert_Y coordinates", invertYStatic );
		gd.addCheckbox("Ignore_Z_stage position", ignoreZStageStatic);
		             
		gd.addChoice( "Fusion_Method", methodListCollection, fusionMethodStatic );
		gd.addNumericField( "Fusion alpha", alphaStatic, 2 );
		gd.addNumericField( "Regression Threshold", thresholdRStatic, 2 );
		gd.addNumericField( "Max/Avg Displacement Threshold", thresholdDisplacementRelativeStatic, 2 );		
		gd.addNumericField( "Absolute Avg Displacement Threshold", thresholdDisplacementAbsoluteStatic, 2 );		
		gd.addCheckbox("Create_only_preview", previewOnlyStatic);
		gd.addMessage( "" );
		gd.addMessage( "This Plugin is developed by Stephan Preibisch\n" + myURL );

		MultiLineLabel text = (MultiLineLabel) gd.getMessage();
		addHyperLinkListener(text, myURL);

		gd.showDialog();
		if (gd.wasCanceled()) 
			return;

		String fileName = gd.getNextString();
		fileNameStatic = fileName;

		boolean computeOverlap = gd.getNextBoolean();
		computeOverlapStatic = computeOverlap;

		boolean ignoreCalibration = gd.getNextBoolean();
		ignoreCalibrationStatic = ignoreCalibration;
		
		double overlap = gd.getNextNumber();
		overlapStatic = overlap;

		boolean invertX = gd.getNextBoolean();
		invertXStatic = invertX;

		boolean invertY = gd.getNextBoolean();
		invertYStatic = invertY;

		boolean ignoreZStage = gd.getNextBoolean();
		ignoreZStageStatic = ignoreZStage;

		String fusionMethod = gd.getNextChoice();
		fusionMethodStatic = fusionMethod;
		
		this.alpha = gd.getNextNumber();
		alphaStatic = alpha;
		
		this.thresholdR = gd.getNextNumber();
		thresholdRStatic = thresholdR;
		
		this.thresholdDisplacementRelative = gd.getNextNumber();
		thresholdDisplacementRelativeStatic = thresholdDisplacementRelative;
		
		this.thresholdDisplacementAbsolute = gd.getNextNumber();
		thresholdDisplacementAbsoluteStatic = thresholdDisplacementAbsolute;
		
		boolean previewOnly = gd.getNextBoolean();
		previewOnlyStatic = previewOnly;

		ArrayList<ImageInformation> imageInformationList = parseMultiSeriesFile( fileName, overlap,  ignoreCalibration, invertX, invertY, ignoreZStage );
		
		if ( imageInformationList == null )
			return;
		
		for ( ImageInformation iI : imageInformationList )
		{
			Log.info( iI.imageName );
			
			String offset = "";
			for ( int d = 0; d < iI.offset.length; ++d )
				offset += iI.offset[ d ] + ", ";
			
			Log.info( offset );
		}

		final GridLayout gridLayout = new GridLayout();

		gridLayout.imageInformationList = imageInformationList;
		gridLayout.fusionMethod = fusionMethod;
		gridLayout.alpha = this.alpha;
		gridLayout.thresholdR = this.thresholdR;
		gridLayout.thresholdDisplacementRelative = this.thresholdDisplacementRelative;
		gridLayout.thresholdDisplacementAbsolute = this.thresholdDisplacementAbsolute;
		gridLayout.dim = imageInformationList.get( 0 ).dim;
		gridLayout.rgbOrder = rgbTypes[0];

		new Stitch_Image_Collection().work( gridLayout, previewOnly, computeOverlap, fileName + ".txt", true );
	}

	protected ArrayList<ImageInformation> parseMultiSeriesFile( final String filename, final double increaseOverlap, final boolean ignoreCalibration, final boolean invertX, final boolean invertY, final boolean ignoreZStage )
	{
		if ( filename == null || filename.length() == 0 )
		{
			Log.error( "Filename is empty!" );
			return null;
		}
		
		final ArrayList<ImageInformation> imageInformationList = new ArrayList<ImageInformation>();

		final IFormatReader r = new ChannelSeparator();
		
		try 
		{
			final ServiceFactory factory = new ServiceFactory();
			final OMEXMLService service = factory.getInstance( OMEXMLService.class );
			final IMetadata meta = service.createOMEXMLMetadata();
			r.setMetadataStore( meta );

			r.setId( filename );

			final int numSeries = r.getSeriesCount();
			
			Log.debug( "numSeries:  " + numSeries );
			
			if ( numSeries == 1 )
			{
				Log.error( "File contains only one tile: " + filename );
				return null;
			}
			
			// get maxZ
			int dim = 2;
			for ( int series = 0; series < numSeries; ++series )
				if ( r.getSizeZ() > 1 )
					dim = 3;

			Log.debug( "dim:  " + dim );

			for ( int series = 0; series < numSeries; ++series )
			{
				Log.debug( "fetching data for series:  " + series );
				r.setSeries( series );

				final MetadataRetrieve retrieve = service.asRetrieve(r.getMetadataStore());

				// stage coordinates (per plane and series)
				double[] location = CommonFunctions.getPlanePosition( r, retrieve, series, 0, invertX, invertY, ignoreZStage );
				double locationX = location[0];
				double locationY = location[1];
				double locationZ = location[2];

				if ( !ignoreCalibration )
				{
					// calibration
					double calX = 1, calY = 1, calZ = 1;
					Length cal;
					final String dimOrder = r.getDimensionOrder().toUpperCase();
					
					final int posX = dimOrder.indexOf( 'X' );
					cal = retrieve.getPixelsPhysicalSizeX( 0 );
					if ( posX >= 0 && cal != null && cal.value().doubleValue() != 0 )
						calX = cal.value().doubleValue();
	
					Log.debug( "calibrationX:  " + calX );
	
					final int posY = dimOrder.indexOf( 'Y' );
					cal = retrieve.getPixelsPhysicalSizeY( 0 );
					if ( posY >= 0 && cal != null && cal.value().doubleValue() != 0 )
						calY = cal.value().doubleValue();
	
					Log.debug( "calibrationY:  " + calY );
	
					final int posZ = dimOrder.indexOf( 'Z' );
					cal = retrieve.getPixelsPhysicalSizeZ( 0 );
					if ( posZ >= 0 && cal != null && cal.value().doubleValue() != 0 )
						calZ = cal.value().doubleValue();
				
					Log.debug( "calibrationZ:  " + calZ );
	
					// location in pixel values;
					locationX /= calX;
					locationY /= calY;
					locationZ /= calZ;
				}
				Log.debug( "locationX [px]:  " + locationX );
				Log.debug( "locationY [px]:  " + locationY );
				Log.debug( "locationZ [px]:  " + locationZ );

				// increase overlap if desired
				locationX *= (100.0-increaseOverlap)/100.0;
				locationY *= (100.0-increaseOverlap)/100.0;
				locationZ *= (100.0-increaseOverlap)/100.0;
				
				// create ImageInformationList
				
				final ImageInformation iI;
				
				if ( dim == 2 )
				{
					iI = new ImageInformation( dim, series, new TranslationModel2D() );
					
					iI.offset[0] = (float)locationX; 
					iI.position[0] = iI.offset[0]; 
					iI.offset[1] = (float)locationY; 
					iI.position[1] = iI.offset[1]; 
				}
				else
				{
					iI = new ImageInformation( dim, series, new TranslationModel3D() );
					iI.offset[0] = (float)locationX; 
					iI.position[0] = iI.offset[0]; 
					iI.offset[1] = (float)locationY; 
					iI.position[1] = iI.offset[1]; 
					iI.offset[2] = (float)locationZ; 
					iI.position[2] = iI.offset[2]; 
				}
				
				iI.imageName = filename;
				iI.imp = null;
				iI.seriesNumber = series;
				
				imageInformationList.add( iI );
			}
		}
		catch ( Exception ex ) 
		{ 
			Log.error(ex);
			return null; 
		}
		
		return imageInformationList;
	}

}
