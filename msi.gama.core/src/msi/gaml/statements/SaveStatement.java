/*******************************************************************************************************
 *
 * msi.gaml.statements.SaveStatement.java, in plugin msi.gama.core,
 * is part of the source code of the GAMA modeling and simulation platform (v. 1.8)
 * 
 * (c) 2007-2018 UMI 209 UMMISCO IRD/SU & Partners
 *
 * Visit https://github.com/gama-platform/gama for license information and contacts.
 * 
 ********************************************************************************************************/
package msi.gaml.statements;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.ArrayUtils;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridCoverageFactory;
import org.geotools.data.DataUtilities;
import org.geotools.data.FeatureWriter;
import org.geotools.data.Transaction;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.gce.geotiff.GeoTiffWriter;
import org.geotools.gce.image.WorldImageWriter;
import org.geotools.geojson.feature.FeatureJSON;
import org.geotools.geometry.Envelope2D;
import org.geotools.referencing.CRS;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.vividsolutions.jts.algorithm.CGAlgorithms;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.CoordinateSequence;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryCollection;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.MultiPoint;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.geom.impl.CoordinateArraySequenceFactory;

import msi.gama.common.geometry.GeometryUtils;
import msi.gama.common.interfaces.IGamlIssue;
import msi.gama.common.interfaces.IKeyword;
import msi.gama.common.interfaces.ITyped;
import msi.gama.common.util.FileUtils;
import msi.gama.metamodel.agent.IAgent;
import msi.gama.metamodel.population.IPopulation;
import msi.gama.metamodel.shape.GamaShape;
import msi.gama.metamodel.shape.IShape;
import msi.gama.metamodel.topology.grid.GamaSpatialMatrix.GridPopulation;
import msi.gama.metamodel.topology.projection.IProjection;
import msi.gama.precompiler.GamlAnnotations.doc;
import msi.gama.precompiler.GamlAnnotations.example;
import msi.gama.precompiler.GamlAnnotations.facet;
import msi.gama.precompiler.GamlAnnotations.facets;
import msi.gama.precompiler.GamlAnnotations.inside;
import msi.gama.precompiler.GamlAnnotations.symbol;
import msi.gama.precompiler.GamlAnnotations.usage;
import msi.gama.precompiler.IConcept;
import msi.gama.precompiler.ISymbolKind;
import msi.gama.runtime.IScope;
import msi.gama.runtime.exceptions.GamaRuntimeException;
import msi.gama.util.GamaListFactory;
import msi.gama.util.GamaMapFactory;
import msi.gama.util.IList;
import msi.gama.util.IModifiableContainer;
import msi.gama.util.file.IGamaFile;
import msi.gama.util.graph.IGraph;
import msi.gama.util.graph.writer.AvailableGraphWriters;
import msi.gaml.compilation.IDescriptionValidator;
import msi.gaml.compilation.annotations.validator;
import msi.gaml.descriptions.IDescription;
import msi.gaml.descriptions.SpeciesDescription;
import msi.gaml.descriptions.StatementDescription;
import msi.gaml.expressions.IExpression;
import msi.gaml.expressions.MapExpression;
import msi.gaml.operators.Cast;
import msi.gaml.operators.Comparison;
import msi.gaml.operators.Strings;
import msi.gaml.species.ISpecies;
import msi.gaml.statements.SaveStatement.SaveValidator;
import msi.gaml.types.GamaFileType;
import msi.gaml.types.GamaKmlExport;
import msi.gaml.types.IType;
import msi.gaml.types.Types;

@symbol (
		name = IKeyword.SAVE,
		kind = ISymbolKind.SINGLE_STATEMENT,
		concept = { IConcept.FILE, IConcept.SAVE_FILE },
		with_sequence = false,
		with_args = true,
		remote_context = true)
@inside (
		kinds = { ISymbolKind.BEHAVIOR, ISymbolKind.ACTION })
@facets (
		value = { @facet (
				name = IKeyword.TYPE,
				type = IType.ID,
				optional = true,
				values = { "shp", "text", "csv", "asc", "geotiff", "image", "kml", "kmz", "json" },
				doc = @doc ("an expression that evaluates to an string, the type of the output file (it can be only \"shp\", \"asc\", \"geotiff\", \"image\", \"text\" or \"csv\") ")),
				@facet (
						name = IKeyword.DATA,
						type = IType.NONE,
						optional = true,
						doc = @doc ("any expression, that will be saved in the file")),
				@facet (
						name = IKeyword.REWRITE,
						type = IType.BOOL,
						optional = true,
						doc = @doc ("an expression that evaluates to a boolean, specifying whether the save will ecrase the file or append data at the end of it. Default is true")),
				@facet (
						name = IKeyword.HEADER,
						type = IType.BOOL,
						optional = true,
						doc = @doc ("an expression that evaluates to a boolean, specifying whether the save will write a header if the file does not exist")),
				@facet (
						name = IKeyword.TO,
						type = IType.STRING,
						optional = true,
						doc = @doc ("an expression that evaluates to an string, the path to the file, or directly to a file")),
				@facet (
						name = "crs",
						type = IType.NONE,
						optional = true,
						doc = @doc ("the name of the projection, e.g. crs:\"EPSG:4326\" or its EPSG id, e.g. crs:4326. Here a list of the CRS codes (and EPSG id): http://spatialreference.org")),
				@facet (
						name = IKeyword.ATTRIBUTES,
						type = { IType.MAP },
						optional = true,
						doc = @doc (
								value = "Allows to specify the attributes of a shape file where agents are saved. The keys of the map are the names of the attributes that will be present in the file, the values are whatever expressions neeeded to define their value")),
				@facet (
						name = IKeyword.WITH,
						type = { IType.MAP },
						optional = true,
						doc = @doc (
								deprecated = "Please use 'attributes:' instead",
								value = "Allows to define the attributes of a shape file. Keys of the map are the attributes of agents to save, values are the names of attributes in the shape file")) },
		omissible = IKeyword.DATA)
@doc (
		value = "Allows to save data in a file. The type of file can be \"shp\", \"asc\", \"geotiff\", \"text\" or \"csv\".",
		usages = { @usage (
				value = "Its simple syntax is:",
				examples = { @example (
						value = "save data to: output_file type: a_type_file;",
						isExecutable = false) }),
				@usage (
						value = "To save data in a text file:",
						examples = { @example (
								value = "save (string(cycle) + \"->\"  + name + \":\" + location) to: \"save_data.txt\" type: \"text\";") }),
				@usage (
						value = "To save the values of some attributes of the current agent in csv file:",
						examples = { @example (
								value = "save [name, location, host] to: \"save_data.csv\" type: \"csv\";") }),
				@usage (
						value = "To save the values of all attributes of all the agents of a species into a csv (with optional attributes):",
						examples = { @example (
								value = "save species_of(self) to: \"save_csvfile.csv\" type: \"csv\" header: false;") }),
				@usage (
						value = "To save the geometries of all the agents of a species into a shapefile (with optional attributes):",
						examples = { @example (
								value = "save species_of(self) to: \"save_shapefile.shp\" type: \"shp\" with: [name::\"nameAgent\", location::\"locationAgent\"] crs: \"EPSG:4326\";") }),
				@usage (
						value = "To save the grid_value attributes of all the cells of a grid into an ESRI ASCII Raster file:",
						examples = { @example (
								value = "save grid to: \"save_grid.asc\" type: \"asc\";") }),
				@usage (
						value = "To save the grid_value attributes of all the cells of a grid into geotiff:",
						examples = { @example (
								value = "save grid to: \"save_grid.tif\" type: \"geotiff\";") }),
				@usage (
						value = "To save the grid_value attributes of all the cells of a grid into png (with a worldfile):",
						examples = { @example (
								value = "save grid to: \"save_grid.png\" type: \"image\";") }),
				@usage (
						value = "The save statement can be use in an init block, a reflex, an action or in a user command. Do not use it in experiments.") })
@validator (SaveValidator.class)
@SuppressWarnings ({ "rawtypes" })
public class SaveStatement extends AbstractStatementSequence implements IStatement.WithArgs {

	public static class SaveValidator implements IDescriptionValidator<StatementDescription> {

		/**
		 * Method validate()
		 * 
		 * @see msi.gaml.compilation.IDescriptionValidator#validate(msi.gaml.descriptions.IDescription)
		 */
		@Override
		public void validate(final StatementDescription description) {

			final StatementDescription desc = description;
			final Facets args = desc.getPassedArgs();
			final IExpression att = desc.getFacetExpr(ATTRIBUTES);
			if (att != null) {
				if (args != null && !args.isEmpty()) {
					desc.warning(
							"'with' and 'attributes' are mutually exclusive. Only the first one will be considered",
							IGamlIssue.CONFLICTING_FACETS, ATTRIBUTES, WITH);
				}
				final IExpression type = desc.getFacetExpr(TYPE);
				if (type == null || !type.literalValue().equals("shp")) {
					desc.warning("Attributes can only be defined for shape files", IGamlIssue.WRONG_TYPE, ATTRIBUTES);
				}

			}

			final IExpression data = desc.getFacetExpr(DATA);
			if (data == null) { return; }
			final IType<?> t = data.getGamlType().getContentType();
			final SpeciesDescription species = t.getSpecies();

			if (att == null && (args == null || args.isEmpty())) { return; }
			if (species == null) {
				desc.error("Attributes can only be saved for agents", IGamlIssue.UNKNOWN_FACET,
						att == null ? WITH : ATTRIBUTES);
			} else {
				if (args != null) {
					args.forEachEntry((name, exp) -> {
						if (!species.hasAttribute(name)) {
							desc.error(
									"Attribute " + name + " is not defined for the agents of " + data.serialize(false),
									IGamlIssue.UNKNOWN_VAR, WITH);
							return false;
						}
						return true;
					});
				}
			}
		}

	}

	private Arguments withFacet;
	private final IExpression attributesFacet;
	private final IExpression crsCode, item, file, rewriteExpr, header;
	
	public SaveStatement(final IDescription desc) {
		super(desc);
		crsCode = desc.getFacetExpr("crs");
		item = desc.getFacetExpr(IKeyword.DATA);
		file = getFacet(IKeyword.TO);
		rewriteExpr = getFacet(IKeyword.REWRITE);
		header = getFacet(IKeyword.HEADER);
		attributesFacet = getFacet(IKeyword.ATTRIBUTES);
	}

	private boolean shouldOverwrite(final IScope scope) {
		if (rewriteExpr == null) { return true; }
		return Cast.asBool(scope, rewriteExpr.value(scope));
	}

	// TODO rewrite this with the GamaFile framework

	@SuppressWarnings ("unchecked")
	@Override
	public Object privateExecuteIn(final IScope scope) throws GamaRuntimeException {
		// First case: we have a file as item;
		if (file == null && Types.FILE.isAssignableFrom(item.getGamlType())) {
			final IGamaFile file = (IGamaFile) item.value(scope);
			if (file != null) {
				// Passes directly the facets of the statement, like crs, etc.
				file.save(scope, description.getFacets());
			}
			return file;
		}
		final String typeExp = getLiteral(IKeyword.TYPE);
		// Second case: a filename is indicated but not the type. In that case,
		// we try to build a new GamaFile from it and save it
		if (file != null && typeExp == null) {
			final String name = Cast.asString(scope, file.value(scope));
			final Object contents = item.value(scope);
			if (contents instanceof IModifiableContainer) {
				final IGamaFile f = GamaFileType.createFile(scope, name, (IModifiableContainer) contents);
				f.save(scope, description.getFacets());
				return f;
			}

		}

		// These statements will need to be completely rethought because of the
		// possibility to now use the GamaFile infrastructure for this.
		// For instance, TYPE is not needed anymore (the name of the file / its
		// inner type will be enough), like in save json_file("ddd.json",
		// my_map); which we can probably allow to be written save my_map to:
		// json_file("ddd.json"); see #1362

		String path = "";
		if (file == null) { return null; }
		path = FileUtils.constructAbsoluteFilePath(scope, Cast.asString(scope, file.value(scope)), false);
		if (path.equals("")) { return null; }
		String type = "text";
		if (typeExp != null) {
			type = typeExp;
		}
		if (type.equals("shp")) {
			if (item == null) { return null; }
			Object agents = item.value(scope);
			if (agents instanceof ISpecies) {
				agents = scope.getAgent().getPopulationFor((ISpecies) agents);
			}
			if (!(agents instanceof IList)) { return null; }
			saveShape((IList<? extends IShape>) agents, path, scope, false);
		} else  if (type.equals("json")) {
				if (item == null) { return null; }
				Object agents = item.value(scope);
				if (agents instanceof ISpecies) {
					agents = scope.getAgent().getPopulationFor((ISpecies) agents);
				}
				if (!(agents instanceof IList)) { return null; }
				saveShape((IList<? extends IShape>) agents, path, scope, true);
			
		} else if (type.equals("text") || type.equals("csv")) {
			final File fileTxt = new File(path);
			boolean exists = fileTxt.exists();
			final boolean rewrite = shouldOverwrite(scope);
			if (rewrite) {
				if (exists) {
					fileTxt.delete();
					exists = false;
				}
			}

			try {
				createParents(fileTxt);
				fileTxt.createNewFile();
			} catch (final GamaRuntimeException e) {
				throw e;
			} catch (final IOException e) {
				throw GamaRuntimeException.create(e, scope);
			}

			final boolean addHeader = !exists && (header == null || Cast.asBool(scope, header.value(scope)));

			saveText(type, fileTxt, addHeader, scope);

		} else if (type.equals("asc")) {
			ISpecies species;
			if (item == null) { return null; }
			species = Cast.asSpecies(scope, item.value(scope));
			if (species == null || !species.isGrid()) { return null; }

			saveAsc(species, path, scope);
		} else if (type.equals("geotiff") || type.equals("image")) {
			ISpecies species;
			if (item == null) { return null; }
			species = Cast.asSpecies(scope, item.value(scope));
			if (species == null || !species.isGrid()) { return null; }

			saveRasterImage(species, path, scope, type.equals("geotiff"));
		} else if (type.equals("kml")) {
			GamaKmlExport kml;
			if (item == null || !(item.value(scope) instanceof GamaKmlExport)) { return null; }
			kml = (GamaKmlExport) item.value(scope);

			if (kml == null) { return null; }

			exportKML(scope, kml, path);
		} else if (type.equals("kmz")) {
			GamaKmlExport kml;
			if (item == null || !(item.value(scope) instanceof GamaKmlExport)) { return null; }
			kml = (GamaKmlExport) item.value(scope);

			if (kml == null) { return null; }

			exportKMZ(scope, kml, path);
		} else if (AvailableGraphWriters.getAvailableWriters().contains(type.trim().toLowerCase())) {

			IGraph g;
			if (item == null) {
				// scope.setStatus(ExecutionStatus.failure);
				return null;
			}
			g = Cast.asGraph(scope, item);
			if (g == null) {
				// scope.setStatus(ExecutionStatus.failure);
				return null;
			}
			AvailableGraphWriters.getGraphWriter(type.trim().toLowerCase()).writeGraph(scope, g, null, path);

		} else {

			throw GamaRuntimeException.error("Unable to save, because this format is not recognized ('" + type + "')",
					scope);
		}
		return Cast.asString(scope, file.value(scope));
	}

	private static void exportKML(final IScope scope, final GamaKmlExport kml, final String path) {
		kml.saveAsKml(scope, path);
	}

	private static void exportKMZ(final IScope scope, final GamaKmlExport kml, final String path) {
		kml.saveAsKmz(scope, path);
	}

	private static void createParents(final File outputFile) {
		final File parent = outputFile.getParentFile();
		if (!parent.exists()) {
			parent.mkdirs();
		}

	}

	public void saveAsc(final ISpecies species, final String path, final IScope scope) {
		final File f = new File(path);
		if (f.exists()) {
			f.delete();
		}
		try (FileWriter fw = new FileWriter(f)) {
			String header = "";
			final GridPopulation gp = (GridPopulation) species.getPopulation(scope);
			final int nbCols = gp.getNbCols();
			final int nbRows = gp.getNbRows();
			header += "ncols         " + nbCols + Strings.LN;
			header += "nrows         " + nbRows + Strings.LN;
			final boolean nullProjection = scope.getSimulation().getProjectionFactory().getWorld() == null;
			header += "xllcorner     "
					+ (nullProjection ? "0"
							: scope.getSimulation().getProjectionFactory().getWorld().getProjectedEnvelope().getMinX())
					+ Strings.LN;
			header += "yllcorner     "
					+ (nullProjection ? "0"
							: scope.getSimulation().getProjectionFactory().getWorld().getProjectedEnvelope().getMinY())
					+ Strings.LN;
			final double dx = scope.getSimulation().getEnvelope().getWidth() / nbCols;
			final double dy = scope.getSimulation().getEnvelope().getHeight() / nbRows;
			if (Comparison.equal(dx, dy)) {
				header += "cellsize      " + dx + Strings.LN;
			} else {
				header += "dx            " + dx + Strings.LN;
				header += "dy            " + dy + Strings.LN;
			}
			fw.write(header);

			for (int i = 0; i < nbRows; i++) {
				String val = "";
				for (int j = 0; j < nbCols; j++) {
					val += gp.getGridValue(j, i) + " ";
				}
				fw.write(val + Strings.LN);
			}
		} catch (final IOException e) {
			return;
		}

	}

	public void saveRasterImage(final ISpecies species, final String path, final IScope scope,
			final boolean toGeotiff) {
		final File f = new File(path);
		if (f.exists()) {
			f.delete();
		}
		final GridPopulation gp = (GridPopulation) species.getPopulation(scope);
		final int cols = gp.getNbCols();
		final int rows = gp.getNbRows();

		final float[][] imagePixelData = new float[rows][cols];
		for (int row = 0; row < rows; row++) {
			for (int col = 0; col < cols; col++) {
				imagePixelData[row][col] = gp.getGridValue(col, row).floatValue();
			}

		}
		final boolean nullProjection = scope.getSimulation().getProjectionFactory().getWorld() == null;
		final double x = nullProjection ? 0
				: scope.getSimulation().getProjectionFactory().getWorld().getProjectedEnvelope().getMinX();
		final double y = nullProjection ? 0
				: scope.getSimulation().getProjectionFactory().getWorld().getProjectedEnvelope().getMinY();
		final double width = scope.getSimulation().getEnvelope().getWidth();
		final double height = scope.getSimulation().getEnvelope().getHeight();

		Envelope2D refEnvelope;
		CoordinateReferenceSystem crs = null;
		try {
			crs = nullProjection ? CRS.decode("EPSG:2154")
					: scope.getSimulation().getProjectionFactory().getWorld().getTargetCRS(scope);
		} catch (final NoSuchAuthorityCodeException e1) {
			e1.printStackTrace();
		} catch (final FactoryException e1) {
			e1.printStackTrace();
		}
		refEnvelope = new Envelope2D(crs, x, y, width, height);

		final GridCoverage2D coverage = new GridCoverageFactory().create("data", imagePixelData, refEnvelope);
		try {
			if (toGeotiff) {
				final GeoTiffWriter writer = new GeoTiffWriter(f);
				writer.write(coverage, null);
			} else {
				final WorldImageWriter writer = new WorldImageWriter(f);
				writer.write(coverage, null);

			}

		} catch (final Exception e) {
			e.printStackTrace();
		}
	}

	public static String getGeometryType(final List<? extends IShape> agents) {
		String geomType = "";
		for (final IShape be : agents) {
			final IShape geom = be.getGeometry();
			if (geom != null && geom.getInnerGeometry() != null) {
				geomType = geom.getInnerGeometry().getClass().getSimpleName();
				if (geom.getInnerGeometry().getNumGeometries() > 1) {
					if (geom.getInnerGeometry().getGeometryN(0).getClass() == Point.class) {
						geomType = MultiPoint.class.getSimpleName();
					} else if (geom.getInnerGeometry().getGeometryN(0).getClass() == LineString.class) {
						geomType = MultiLineString.class.getSimpleName();
					} else if (geom.getInnerGeometry().getGeometryN(0).getClass() == Polygon.class) {
						geomType = MultiPolygon.class.getSimpleName();
					}
					break;
				}
			}
		}
		if ("DynamicLineString".equals(geomType)) {
			geomType = LineString.class.getSimpleName();
		}
		return geomType;
	}

	public void saveShape(final IList<? extends IShape> agents, final String path, final IScope scope, boolean geoJson)
			throws GamaRuntimeException {
		if (agents.size() == 1 && agents.get(0).getInnerGeometry() instanceof GeometryCollection) {
			final GeometryCollection collec = (GeometryCollection) agents.get(0).getInnerGeometry();
			final IList<IShape> shapes = GamaListFactory.create();
			for (int i = 0; i < collec.getNumGeometries(); i++) {
				shapes.add(new GamaShape(collec.getGeometryN(i)));
			}
			saveShape(shapes, path, scope,geoJson);
			return;
		}
		final StringBuilder specs = new StringBuilder(agents.size() * 20);
		final String geomType = getGeometryType(agents);
		specs.append("geometry:" + geomType);
		try {
			final SpeciesDescription species = agents instanceof IPopulation
					? (SpeciesDescription) ((IPopulation) agents).getSpecies().getDescription()
					: agents.getGamlType().getContentType().getSpecies();
			final Map<String, IExpression> attributes = GamaMapFactory.create();
			if (species != null) {
				if (withFacet != null) {
					computeInitsFromWithFacet(scope, withFacet, attributes, species);
				} else if (attributesFacet != null) {
					computeInitsFromAttributesFacet(scope, attributesFacet, attributes, species);
				}
				for (final String e : attributes.keySet()) {
					final IExpression var = attributes.get(e);
					String name = e.replaceAll("\"", "");
					name = name.replaceAll("'", "");
					final String type = type(var);
					specs.append(',').append(name).append(':').append(type);
				}
			}

			if(! geoJson) 
				saveShapeFile(scope, path, agents, specs.toString(), attributes, defineProjection(scope, path));
			else 
				saveGeoJSonFile(scope, path, agents, specs.toString(), attributes, defineProjection(scope, path));
		} catch (final GamaRuntimeException e) {
			throw e;
		} catch (final Throwable e) {
			throw GamaRuntimeException.create(e, scope);
		}

	}

	
	public void saveGeoJson(final IList<? extends IShape> agents, final String path, final IScope scope)
			throws GamaRuntimeException {
		if (agents.size() == 1 && agents.get(0).getInnerGeometry() instanceof GeometryCollection) {
			final GeometryCollection collec = (GeometryCollection) agents.get(0).getInnerGeometry();
			final IList<IShape> shapes = GamaListFactory.create();
			for (int i = 0; i < collec.getNumGeometries(); i++) {
				shapes.add(new GamaShape(collec.getGeometryN(i)));
			}
			saveGeoJson(shapes, path, scope);
			return;
		}
		final StringBuilder specs = new StringBuilder(agents.size() * 20);
		final String geomType = getGeometryType(agents);
		specs.append("geometry:" + geomType);
		try {
			final SpeciesDescription species = agents instanceof IPopulation
					? (SpeciesDescription) ((IPopulation) agents).getSpecies().getDescription()
					: agents.getGamlType().getContentType().getSpecies();
			final Map<String, IExpression> attributes = GamaMapFactory.create();
			if (species != null) {
				if (withFacet != null) {
					computeInitsFromWithFacet(scope, withFacet, attributes, species);
				} else if (attributesFacet != null) {
					computeInitsFromAttributesFacet(scope, attributesFacet, attributes, species);
				}
				for (final String e : attributes.keySet()) {
					final IExpression var = attributes.get(e);
					String name = e.replaceAll("\"", "");
					name = name.replaceAll("'", "");
					final String type = type(var);
					specs.append(',').append(name).append(':').append(type);
				}
			}
			
			saveShapeFile(scope, path, agents, specs.toString(), attributes, defineProjection(scope, path));
		} catch (final GamaRuntimeException e) {
			throw e;
		} catch (final Throwable e) {
			throw GamaRuntimeException.create(e, scope);
		}

	}

	
	public IProjection defineProjection(final IScope scope, final String path) {
		String code = null;
		if (crsCode != null) {
			final IType type = crsCode.getGamlType();
			if (type.id() == IType.INT || type.id() == IType.FLOAT) {
				code = "EPSG:" + Cast.asInt(scope, crsCode.value(scope));
			} else if (type.id() == IType.STRING) {
				code = (String) crsCode.value(scope);
			}
		}
		IProjection gis;
		if (code == null) {
			gis = scope.getSimulation().getProjectionFactory().getWorld();
		} else {
			try {
				gis = scope.getSimulation().getProjectionFactory().forSavingWith(scope, code);
			} catch (final FactoryException e1) {
				throw GamaRuntimeException.error("The code " + code
						+ " does not correspond to a known EPSG code. GAMA is unable to save " + path, scope);
			}
		}
		return gis;
	}

	public void saveText(final String type, final File fileTxt, final boolean header, final IScope scope)
			throws GamaRuntimeException {
		try (FileWriter fw = new FileWriter(fileTxt, true)) {
			if (item == null) { return; }
			if (type.equals("text")) {
				fw.write(Cast.asString(scope, item.value(scope)) + Strings.LN);
			} else if (type.equals("csv")) {
				final IType itemType = item.getGamlType();
				final SpeciesDescription sd;
				if (itemType.isAgentType()) {
					sd = itemType.getSpecies();
				} else if (itemType.getContentType().isAgentType()) {
					sd = itemType.getContentType().getSpecies();
				} else {
					sd = null;
				}
				final Object value = item.value(scope);
				final IList values = itemType.isContainer() ? Cast.asList(scope, value)
						: GamaListFactory.create(scope, itemType, value);
				if (values.isEmpty()) { return; }
				if (sd != null) {
					final Collection<String> attributeNames = sd.getAttributeNames();
					attributeNames.removeAll(NON_SAVEABLE_ATTRIBUTE_NAMES);
					if (header) {
						// final IAgent ag0 = Cast.asAgent(scope,
						// values.get(0));
						fw.write("cycle;name;location.x;location.y;location.z");
						for (final String v : attributeNames) {
							fw.write(";" + v);
						}
						fw.write(Strings.LN);
					}
					for (final Object obj : values) {
						if (obj instanceof IAgent) {
							final IAgent ag = Cast.asAgent(scope, obj);
							fw.write(scope.getClock().getCycle() + ";" + ag.getName().replace(';', ',') + ";"
									+ ag.getLocation().getX() + ";" + ag.getLocation().getY() + ";"
									+ ag.getLocation().getZ());
							for (final String v : attributeNames) {
								String val = Cast.toGaml(ag.getDirectVarValue(scope, v)).replace(';', ',');
								if (val.startsWith("'") && val.endsWith("'")
										|| val.startsWith("\"") && val.endsWith("\"")) {
									val = val.substring(1, val.length() - 1);
								}
								fw.write(";" + val);
							}
							fw.write(Strings.LN);
						}

					}
				} else {
					if (header) {
						fw.write(item.serialize(true).replace("]", "").replace("[", ""));
						fw.write(Strings.LN);
					}
					if (itemType.id() == IType.MATRIX) {
						final String[] tmpValue = value.toString().replace("[", "").replace("]", "").split(",");
						for (int i = 0; i < tmpValue.length; i++) {
							if (i > 0) {
								fw.write(',');
							}
							fw.write(toCleanString(tmpValue[i]));
						}
						fw.write(Strings.LN);
					} else {
						final int size = values.size();
						for (int i = 0; i < size; i++) {
							if (i > 0) {
								fw.write(',');
							}
							fw.write(toCleanString(values.get(i)));
						}
						fw.write(Strings.LN);
					}
				}

			}

		} catch (final GamaRuntimeException e) {
			throw e;
		} catch (final Throwable e) {
			throw GamaRuntimeException.create(e, scope);
		}

	}

	public String toCleanString(final Object o) {
		String val = Cast.toGaml(o).replace(';', ',');
		if (val.startsWith("'") && val.endsWith("'") || val.startsWith("\"") && val.endsWith("\"")) {
			val = val.substring(1, val.length() - 1);
		}

		if (o instanceof String) {
			val = val.replace("\\'", "'");
			val = val.replace("\\\"", "\"");

		}
		return val;
	}

	public String type(final ITyped var) {
		switch (var.getGamlType().id()) {
			case IType.BOOL:
				return "Boolean";
			case IType.INT:
				return "Integer";
			case IType.FLOAT:
				return "Double";
			default:
				return "String";
		}
	}

	private static final Set<String> NON_SAVEABLE_ATTRIBUTE_NAMES = new HashSet<>(Arrays.asList(IKeyword.PEERS,
			IKeyword.LOCATION, IKeyword.HOST, IKeyword.AGENTS, IKeyword.MEMBERS, IKeyword.SHAPE));

	private void computeInitsFromWithFacet(final IScope scope, final Arguments withFacet,
			final Map<String, IExpression> values, final SpeciesDescription species) throws GamaRuntimeException {
		if (withFacet.isEmpty() && species != null) {
			for (final String var : species.getAttributeNames()) {
				if (!NON_SAVEABLE_ATTRIBUTE_NAMES.contains(var)) {
					values.put(var, species.getVarExpr(var, false));
				}
			}
		} else {
			withFacet.forEach((key, value) -> {
				values.put(value.getExpression().literalValue(), species.getVarExpr(key, false));
			});
		}
	}

	private void computeInitsFromAttributesFacet(final IScope scope, final IExpression attributesFacet,
			final Map<String, IExpression> values, final SpeciesDescription species) throws GamaRuntimeException {
		if (attributesFacet instanceof MapExpression) {
			final Map<IExpression, IExpression> map = ((MapExpression) attributesFacet).getElements();
			map.forEach((key, value) -> {
				final String name = Cast.asString(scope, key.value(scope));
				values.put(name, value);
			});
		}
	}

	private static Geometry fixesPolygonCWS(final Geometry g) {
		if (g instanceof Polygon) {
			final Polygon p = (Polygon) g;
			final boolean clockwise = CGAlgorithms.isCCW(p.getExteriorRing().getCoordinates());
			if (p.getNumInteriorRing() == 0) { return g; }
			boolean change = false;
			final LinearRing[] holes = new LinearRing[p.getNumInteriorRing()];
			final GeometryFactory geomFact = new GeometryFactory();
			for (int i = 0; i < p.getNumInteriorRing(); i++) {
				final LinearRing hole = (LinearRing) p.getInteriorRingN(i);
				if (!clockwise && !CGAlgorithms.isCCW(hole.getCoordinates())
						|| clockwise && CGAlgorithms.isCCW(hole.getCoordinates())) {
					change = true;
					final Coordinate[] coords = hole.getCoordinates();
					ArrayUtils.reverse(coords);
					final CoordinateSequence points = CoordinateArraySequenceFactory.instance().create(coords);
					holes[i] = new LinearRing(points, geomFact);
				} else {
					holes[i] = hole;
				}
			}
			if (change) { return geomFact.createPolygon((LinearRing) p.getExteriorRing(), holes); }
		} else if (g instanceof GeometryCollection) {
			final GeometryCollection gc = (GeometryCollection) g;
			boolean change = false;
			final GeometryFactory geomFact = new GeometryFactory();
			final Geometry[] geometries = new Geometry[gc.getNumGeometries()];
			for (int i = 0; i < gc.getNumGeometries(); i++) {
				final Geometry gg = gc.getGeometryN(i);
				if (gg instanceof Polygon) {
					geometries[i] = fixesPolygonCWS(gg);
					change = true;
				} else {
					geometries[i] = gg;
				}
			}
			if (change) { return geomFact.createGeometryCollection(geometries); }
		}
		return g;
	}

		public static boolean buildFeature(IScope scope, SimpleFeature ff, IShape ag, final IProjection gis, Collection<IExpression> attributeValues) {
			List<Object> values = new ArrayList<>();
			// geometry is by convention (in specs) at position 0
			if (ag.getInnerGeometry() == null) {
				return false ;
			}
			//System.out.println("ag.getInnerGeometry(): "+ ag.getInnerGeometry().getClass());
			
			Geometry g = gis == null ? ag.getInnerGeometry() : gis.inverseTransform(ag.getInnerGeometry());

			g = fixesPolygonCWS(g);
			g = geometryCollectionManagement(g);

			values.add(g);
			if (ag instanceof IAgent) {
				for (final IExpression variable : attributeValues) {
					Object val = scope.evaluate(variable, (IAgent) ag).getValue();
					if (variable.getGamlType().equals(Types.STRING)) {
						if (val == null) {
							val = "";
						} else {
							final String val2 = val.toString();
							if (val2.startsWith("'") && val2.endsWith("'")
									|| val2.startsWith("\"") && val2.endsWith("\"")) {
								val = val2.substring(1, val2.length() - 1);
							}
						}
					}
					values.add(val);
				}
			}
			// AD Assumes that the type is ok.
			// AD TODO replace this list of variable names by expressions
			// (to be
			// evaluated by agents), so that dynamic values can be passed
			// AD WARNING Would require some sort of iterator operator that
			// would collect the values beforehand
			ff.setAttributes(values);
			return true;
		}
	
	
	// AD 2/1/16 Replace IAgent by IShape so as to be able to save geometries
		public static void saveGeoJSonFile(final IScope scope, final String path, final List<? extends IShape> agents,
				/* final String featureTypeName, */final String specs, final Map<String, IExpression> attributes,
				final IProjection gis) throws IOException, SchemaException, GamaRuntimeException {
			// AD 11/02/15 Added to allow saving to new directories
			if (agents == null || agents.isEmpty()) return;
			final File f = new File(path);
			createParents(f);
			
			// The name of the type and the name of the feature source shoud now be
			// the same.
			final SimpleFeatureType type =
					DataUtilities.createType("geojson", specs);
			 SimpleFeatureBuilder builder = new SimpleFeatureBuilder(type);
			 DefaultFeatureCollection featureCollection = new DefaultFeatureCollection();
		
				// AD Builds once the list of agent attributes to evaluate
				final Collection<IExpression> attributeValues =
						attributes == null ? Collections.EMPTY_LIST : attributes.values();
				int i = 0;
				for (final IShape ag : agents) {
					final SimpleFeature ff = builder.buildFeature( i+"");
					i++;
					boolean ok =  buildFeature( scope, ff, ag,  gis, attributeValues);
					if (! ok) {
						continue;
					}	
					featureCollection.add(ff);
				}
				
				 FeatureJSON io = new FeatureJSON();
				 io.writeFeatureCollection(featureCollection, path);
			
			
		}
	// AD 2/1/16 Replace IAgent by IShape so as to be able to save geometries
	public static void saveShapeFile(final IScope scope, final String path, final List<? extends IShape> agents,
			/* final String featureTypeName, */final String specs, final Map<String, IExpression> attributes,
			final IProjection gis) throws IOException, SchemaException, GamaRuntimeException {
		// AD 11/02/15 Added to allow saving to new directories
		if (agents == null || agents.isEmpty()) return;
		final File f = new File(path);
		createParents(f);

		final ShapefileDataStore store = new ShapefileDataStore(f.toURI().toURL());
		store.setCharset(Charset.forName("UTF8"));
		// The name of the type and the name of the feature source shoud now be
		// the same.
		final SimpleFeatureType type =
				DataUtilities.createType(store.getFeatureSource().getEntry().getTypeName(), specs);
		store.createSchema(type);
		// AD: creation of a FeatureWriter on the store.
		try (FeatureWriter fw = store.getFeatureWriter(Transaction.AUTO_COMMIT)) {

			// AD Builds once the list of agent attributes to evaluate
			final Collection<IExpression> attributeValues =
					attributes == null ? Collections.EMPTY_LIST : attributes.values();
			for (final IShape ag : agents) {
				final SimpleFeature ff = (SimpleFeature) fw.next();
				boolean ok =  buildFeature( scope, ff, ag,  gis, attributeValues);
				if (! ok) {
					continue;
				}	
			}
			// store.dispose();
			if (gis != null) {
				writePRJ(scope, path, gis);
			}
		} catch (final ClassCastException e) {
			throw GamaRuntimeException.error(
					"Cannot save agents/geometries with different types of geometries (point, line, polygon) in a same shapefile",
					scope);
		} finally {
			store.dispose();
		}
	}
	
	private static Geometry geometryCollectionManagement(Geometry gg) {
		if (gg instanceof GeometryCollection) {
			boolean isMultiPolygon = true;
			boolean isMultiPoint = true;
			boolean isMultiLine = true;
			int nb = ((GeometryCollection)gg).getNumGeometries();
			for (int i = 0; i < nb ; i++) {
				Geometry g = (((GeometryCollection)gg)).getGeometryN(i);
				if (!(g instanceof Polygon)) isMultiPolygon = false;
				if (!(g instanceof LineString)) isMultiLine = false;
				if (!(g instanceof Point)) isMultiPoint = false;
			}
			if (isMultiPolygon) {
				Polygon[] polygons = new Polygon[nb];
				for (int i = 0; i < nb ; i++) {polygons[i] = (Polygon)  (((GeometryCollection)gg)).getGeometryN(i);}
				return GeometryUtils.GEOMETRY_FACTORY.createMultiPolygon(polygons);
			} if (isMultiLine) {
				LineString[] lines = new LineString[nb];
				for (int i = 0; i < nb ; i++) {lines[i] = (LineString)  (((GeometryCollection)gg)).getGeometryN(i);}
				return GeometryUtils.GEOMETRY_FACTORY.createMultiLineString(lines);
			} if (isMultiPoint) {
				Point[] points = new Point[nb];
				for (int i = 0; i < nb ; i++) {points[i] = (Point)  (((GeometryCollection)gg)).getGeometryN(i);}
				return GeometryUtils.GEOMETRY_FACTORY.createMultiPoint(points);
			}
		}
		return gg;
	}

	private static void writePRJ(final IScope scope, final String path, final IProjection gis) {
		final CoordinateReferenceSystem crs = gis.getInitialCRS(scope);
		if (crs != null) {
			try (FileWriter fw = new FileWriter(path.replace(".shp", ".prj"))) {
				fw.write(crs.toString());
			} catch (final IOException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void setFormalArgs(final Arguments args) {
		withFacet = args;
	}

	@Override
	public void setRuntimeArgs(final IScope scope, final Arguments args) {
		// TODO Auto-generated method stub
	}
}
