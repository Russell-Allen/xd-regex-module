package com.allenru.xd.module.regex;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.xd.tuple.Tuple;
import org.springframework.xd.tuple.TupleBuilder;


/**
 * {@link RegexParseToTuple} is a Spring XD Module that processes the input payload (string)
 * through a Regular Expression parser and outputs a {@link Tuple} where each tuple field is
 * a capture group from the regular expression.
 * 
 * For example, given:
 * <pre>
 * 		payload:  "abc 123 efg 456 end"
 * 		RegEx:	"(?&lt;alpha&gt;[a-z]+) ([0-9]+)"
 * </pre>
 * Two tuples are produced, one for each matched region of the payload.  The tuples are:
 * <pre>
 * 		{"alpha":"abc","0":"abc 123","1":"abc","2":"123"}
 * 		{"alpha":"dff","0":"dff 323","1":"dff","2":"323"}
 * </pre>
 * 
 * Named capture groups, such as alpha in the above example, result in a tuple field of the 
 * same name.  If the capture group is unnamed, then the tuple field is the capture group
 * number (as a string.)  Note that the traditional capture group 0, which contains the match
 * region as a whole, is also passed through as a tuple field.
 * 
 * @author Russell Allen
 */
public class RegexParseToTuple {

	
	private final Pattern pattern;
	private final boolean includeAllCaptureGroups;
	
	private final Set<String> groupNames;
	private final int capturingGroupCount;
	

	/**
	 * Constructs a {@link RegexParseToTuple} instance based on the passed in regular expression.
	 * By default, all capture groups are mapped through to tuple fields.  To suppress unnamed 
	 * capture groups, use the alternate constructor.
	 * 
	 * @param regex  The regular expression on which this module is based.
	 */
	public RegexParseToTuple(String regex) {
		this(regex, true);
	}
	
	/**
	 * Constructs a {@link RegexParseToTuple} instance based on the passed in regular expression,
	 * which will convert named capture groups to tuple fields and unnamed capture groups if the
	 * boolean parameter is true.
	 * 
	 * @param regex  The regular expression on which this module is based.
	 */
	public RegexParseToTuple(String regex, boolean includeAllCaptureGroups) {
		this.includeAllCaptureGroups = includeAllCaptureGroups;
		this.pattern = Pattern.compile(regex);
		this.groupNames = extractGroupNames(this.pattern);
		this.capturingGroupCount = getField(this.pattern, "capturingGroupCount");
	}
	
	
	/**
	 * Called once during initialization of this instance, this method is provided
	 * the pattern object constructed from the regular expression on which this 
	 * instance was created, and this method is expected to return the set of named
	 * capture groups within the regular expression / pattern.
	 * 
	 * Unfortunately, the Java Pattern class does not expose the capture group names.
	 * As a result, this method's default implementation is mildly intrusive in that 
	 * it uses reflection to introspect the private internal field of the Pattern 
	 * instance.  In an ideal world, the Pattern class would expose the capture group
	 * names.
	 * 
	 * If introspection does not work (more secure runtime environments?), then this
	 * class will have to be extended and this method overridden to return the set of
	 * capture group names.
	 * 
	 * @param pattern  The Pattern compiled from the regular expression on which this module is based.
	 * @return Set&lt;String&gt; of all capture group names within the passed in pattern.
	 */
	protected Set<String> extractGroupNames(Pattern pattern) {
		Map<String, Integer> namedGroups = getField(pattern, "namedGroups");
		if (namedGroups != null) {
			// duplicate the set to prevent modification in either direction.
			return new HashSet<>(namedGroups.keySet());
		}
		else {
			return Collections.emptySet();
		}
	}
	
	/**
	 * Called once during initialization of this instance, this method is provided
	 * the pattern object constructed from the regular expression on which this 
	 * instance was created, and this method is expected to return the total number
	 * of capture groups within the regular expression / pattern.
	 * 
	 * Unfortunately, the Java Pattern class does not expose the capture group count.
	 * As a result, this method's default implementation is mildly intrusive in that 
	 * it uses reflection to introspect the private internal field of the Pattern 
	 * instance.  In an ideal world, the Pattern class would expose the capture group
	 * count.
	 * 
	 * If introspection does not work (more secure runtime environments?), then this
	 * class will have to be extended and this method overridden to return the 
	 * capture group count.
	 * 
	 * @param pattern  The Pattern compiled from the regular expression on which this module is based.
	 * @return int value of the number of capture groups (named and unnamed)
	 */
	protected int extractCapturingGroupCount(Pattern pattern) {
		return getField(pattern, "capturingGroupCount");
	}

	
	@SuppressWarnings("unchecked")
	private static <T> T getField(Pattern pattern, String fieldName) {
		try {
			Field field = pattern.getClass().getDeclaredField(fieldName);
			field.setAccessible(true);
			return (T) field.get(pattern);
		}
		catch (Exception e) {
			throw new IllegalStateException("Failed to introspect pattern for field "+fieldName, e);
		}
	}
	
	
	/**
	 * This method is called per message, taking the message payload, applying the previously
	 * compiled regular expression pattern, and returning a {@link List} of {@link Tuple}s 
	 * where each tuple represents a matched region of the payload and contains a field per 
	 * capture group (based on the initial configuration of this module.)
	 * 
	 * @param payload    The XD stream payload to be parsed into a tuple.
	 * @return List&lt;Tuple&gt; for each matched region of the payload.
	 */
	public List<Tuple> parse(String payload) {
		final Matcher matcher = pattern.matcher(payload);
		
		List<Tuple> results = new LinkedList<Tuple>();
		
		while (matcher.find()) {
			TupleBuilder tupleBuilder = TupleBuilder.tuple();
			for (String name : groupNames) {
				tupleBuilder.put(name, matcher.group(name));
			}
			if (includeAllCaptureGroups || groupNames.isEmpty()) {
				for(int i = 0; i < capturingGroupCount; i++) {
					tupleBuilder.put(Integer.toString(i), matcher.group(i));
				}
			}
			results.add(tupleBuilder.build());
		}
		
		return results;
	}

	/**
	 * For testing purposes, this module may be executed against one or more values to validate
	 * the regular expression and the resultant tuples.
	 * 
	 * Pass the regular expression as the first command line argument to this class.  Then you may
	 * either (not both) pass test values as the remaining arguments OR via standard in where each
	 * test value is on a separate line.
	 * 
	 * The resulting tuples are printed to standard out.
	 * 
	 * @param args  See usage.
	 * @throws IOException  for the fun of it.
	 */
	public static void main(String[] args) throws IOException {
		if (args.length == 0) {
			System.out.println("CLI usage requires a regular expression as the first argument.  "
					+ "Any following arguments are presumed to be seperate text values that are to "
					+ "be run through this regular expression in order to produce tuples.  "
					+ "If there are no arguments other than the regular expression, then standard in "
					+ "will be read, with erach line treated as a seperate text value to be processed.");
		}
		else if (args.length == 1) {
			RegexParseToTuple parser = new RegexParseToTuple(args[0], true);
			try (InputStream is = System.in) {
				LineNumberReader lnr = new LineNumberReader(new InputStreamReader(is));
				String line = lnr.readLine();
				while (line != null) parser.parse(line).forEach(System.out::println);
			}
		}
		else {
			RegexParseToTuple parser = new RegexParseToTuple(args[0], true);
			Arrays.stream(args)
				.skip(1)	// skip the regular expression argument
				.map(parser::parse)		// apply the parser to the argument to pruduce a list of tuples
				.flatMap(List::stream)	// flatten the stream of lists of tuples into a stream of tuples
				.forEach(System.out::println);	// finally, write out each tuple.
		}
	}

}
