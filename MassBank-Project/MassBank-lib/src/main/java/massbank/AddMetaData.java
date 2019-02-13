package massbank;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.petitparser.context.Result;
import org.petitparser.parser.Parser;


public class AddMetaData {
	private static final Logger logger = LogManager.getLogger(AddMetaData.class);
			
	public static Record validate(String recordstring) {
		// test non standard ASCII chars and print warnings
		for (int i = 0; i < recordstring.length(); i++) {
			if (recordstring.charAt(i) > 0x7F) {
				String[] tokens = recordstring.split("\\r?\\n");
				logger.warn("non standard ASCII character found. This might be an error. Please check carefully.");
				int line = 0, col = 0, offset = 0;
				for (String token : tokens) {
					offset = offset + token.length() + 1;
					if (i < offset) {
						col = i - (offset - (token.length() + 1));
						logger.warn(tokens[line]);
						StringBuilder error_at = new StringBuilder(StringUtils.repeat(" ", tokens[line].length()));
						error_at.setCharAt(col, '^');
						logger.warn(error_at);
						break;
					}
					line++;
				}
			}
		}
		
		Record record = new Record("");
		Parser recordparser = new RecordParser(record);
		Result res = recordparser.parse(recordstring);
		if (res.isFailure()) {
			logger.error(res.getMessage());
			int position = res.getPosition();
			String[] tokens = recordstring.split("\\n");

			int line = 0, col = 0, offset = 0;
			for (String token : tokens) {
				offset = offset + token.length() + 1;
				if (position < offset) {
					col = position - (offset - (token.length() + 1));
					logger.error(tokens[line]);
					StringBuilder error_at = new StringBuilder(StringUtils.repeat(" ", col));
					error_at.append('^');
					logger.error(error_at);
					break;
				}
				line++;
			}
			return null;
		} 
		return record;
	}
	
	public static String doPub(Record record, String recordstring) {
		String publication = record.PUBLICATION();
		if (publication == null) return recordstring;

		String regex_doi = "10\\.\\d{3,9}\\/[\\-\\._;\\(\\)\\/:a-zA-Z0-9]+[a-zA-Z0-9]";

		Pattern pattern_doi = Pattern.compile(".*" + "(" + regex_doi + ")" + ".*");
		Matcher matcher_doi = pattern_doi.matcher(publication);

		String doi;
		if (matcher_doi.matches()) {
			doi = publication.substring(matcher_doi.start(1), matcher_doi.end(1));
			System.out.println(doi);
		}
		// look up https://www.doi.org/ and https://crosscite.org/
		// curl -LH "Accept: text/x-bibliography; style=ieee-with-url" https://doi.org/<doi>
		// eg.
		// curl -LH "Accept: text/x-bibliography; style=ieee-with-url" https://doi.org/10.1038/sdata.2014.29
		// curl -LH "Accept: text/x-bibliography; style=american-chemical-society"  https://data.datacite.org/10.1038/sdata.2014.29
		// gives:
		// [1]S. Beisken, M. Earll, C. Baxter, D. Portwood, Z. Ament, A. Kende, C. Hodgman, G. Seymour, R. Smith, P. Fraser, M. Seymour, R. M. Salek, and C. Steinbeck, “Metabolic differences in ripening of Solanum lycopersicum ‘Ailsa Craig’ and three monogenic mutants,” Scientific Data, vol. 1, p. 140029, Sep. 2014 [Online]. Available: http://dx.doi.org/10.1038/sdata.2014.29

		return recordstring;
	}

	public static void main(String[] arguments) throws Exception {
		boolean doPub = false;
		Options options = new Options();
		options.addOption("a", "all", false, "execute all operations");
		options.addOption("p", "publication", false, "format PUBLICATION tag from given DOI to follow the guidelines of ACS");
		CommandLineParser parser = new DefaultParser();
		CommandLine cmd = null;
		try {
			cmd = parser.parse( options, arguments);
		}
		catch(ParseException exp) {
	        // oops, something went wrong
	        System.err.println( "Parsing command line failed. Reason: " + exp.getMessage() );
	        System.exit(1);
	    }
		
		if (cmd.hasOption("a")) {
			// set all to true
			doPub = true;
		}
		
		if (cmd.hasOption("p")) {
			doPub = true;
		}

		if (!doPub) {
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("AddMetaData [OPTIONS] <FILE>", options);
			System.exit(1);
		}
		
		String filename = null;
		if (cmd.getArgList().size() == 1) filename=cmd.getArgList().get(0);
		else {
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("AddMetaData [OPTIONS] <FILE>", options);
			System.exit(1);
		}
		
		String recordstring = null;
		try {
			recordstring = FileUtils.readFileToString(new File(filename), StandardCharsets.UTF_8);
		}
		catch(IOException exp) {
			System.err.println( "Reading file \""+ filename + "\" failed. Reason: " + exp.getMessage() );
			System.exit(1);
		}
		
		Record record = validate(recordstring);
		if (record == null) {
			System.err.println( "Validation of  \""+ filename + "\" failed. Exiting.");
			System.exit(1);
		}
		
		String recordstring2 = recordstring;
		if (doPub) recordstring2=doPub(record, recordstring2);
		
		//System.out.println(recordstring2);
		
		
		
		System.exit(0);
	}
}
