package homesoil;

import com.google.common.base.*;
import com.google.common.collect.*;
import com.google.common.io.Files;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/**
 * MapFileMap is a map that can be saved to a file as text. All keys and values
 * are converted to strings except maps, which are encoded. When you load from a
 * file, you get a MapFileMap that contains only other MapFileMaps and strings.
 *
 * When you save a map, we convert any lists or object that implement Storable
 * into maps for storage. This class provides methods you can use to convert
 * them back after reading them.
 *
 * @author DanJ
 */
public final class MapFileMap extends HashMap<String, Object> {

    private static final long serialVersionUID = 1L;

    public MapFileMap() {
    }

    public MapFileMap(Map<?, ?> source) {
        super(source.size());

        for (Map.Entry<?, ?> e : source.entrySet()) {
            put(e.getKey().toString(), e.getValue());
        }
    }

    ////////////////////////////////
    // Accessors
    //
    // These methods provide typesafe access to
    // map values that may be kept as strings at times.
    // These will parse the string if needed to get the
    // right data.
    /**
     * This method returns a value as a string; it will call toString() if
     * required to get one.
     *
     * @param key The key of the value.
     * @return The value as a string.
     * @throws IllegalArgumentException If the key is not found.
     */
    public String getString(String key) {
        Object value = getObject(key);
        return value.toString();
    }

    /**
     * This method returns a value as an integer; it will parse the value if
     * required to get one.
     *
     * @param key The key of the value.
     * @return The value as an integer.
     * @throws IllegalArgumentException If the key is not found.
     */
    public int getInteger(String key) {
        return convertToInteger(getObject(key));
    }

    /**
     * This method returns a value as a map file map; it will copy any other map
     * into a map file map, converting they keys to strings if required.
     *
     * @param key The key of the value.
     * @return The value as a MapFileMap; may be a copy of the map within.
     * @throws IllegalArgumentException If the key is not found.
     */
    public MapFileMap getMapFileMap(String key) {
        Object value = getObject(key);

        if (value instanceof MapFileMap) {
            return (MapFileMap) value;
        } else if (value instanceof Map<?, ?>) {
            return new MapFileMap((Map<?, ?>) value);
        }

        throw new ClassCastException(String.format("The value for '%s' is not a map.", key));
    }

    /**
     * This method returns a value as a list; it will convert other collections
     * to lists by copying the elements. If the value is not a list, but is map,
     * this method will try to convert it by treating the keys as indices.
     *
     * @param key The key of the value.
     * @return The value as a list; may be a copy of the list within.
     * @throws IllegalArgumentException If the key is not found.
     */
    public List<?> getList(String key) {
        Object value = getObject(key);

        if (value instanceof List<?>) {
            return (List<?>) value;
        } else if (value instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) value;
            ArrayList<Object> list = Lists.newArrayList();

            for (Map.Entry<?, ?> e : map.entrySet()) {
                int index = convertToInteger(e.getKey());

                while (index >= list.size()) {
                    list.add(null);
                }

                list.set(index, e.getValue());
            }

            return list;
        } else if (value instanceof Collection<?>) {
            return Lists.newArrayList((Collection<?>) value);
        }

        throw new ClassCastException(String.format("The value for '%s' is not a list.", key));
    }

    /**
     * This method returns a value as a list; it will convert other collections
     * to lists by copying the elements. If the value is not a list, but is map,
     * this method will try to convert it by treating the keys as indices.
     *
     * @param key The key of the value.
     * @param itemClass The type of the individual elements.
     * @return The value as a list; always a copy of the list within.
     * @throws IllegalArgumentException If the key is not found.
     */
    public <T extends Storable> List<T> getList(String key, Class<T> itemClass) {
        ArrayList<T> b = Lists.newArrayList();

        for (Object item : getList(key)) {
            b.add(convertValue(item, itemClass));
        }

        return b;
    }

    /**
     * This method returns a value as a set; it will convert other collections
     * to sets by copying the elements. If the value is not a set, but is map,
     * this method will try to convert it using only the values; the keys are
     * ignored.
     *
     * @param key The key of the value.
     * @return The value as a set; may be a copy of the set within.
     * @throws IllegalArgumentException If the key is not found.
     */
    public Set<?> getSet(String key) {
        Object value = getObject(key);

        if (value instanceof Set<?>) {
            return (Set<?>) value;
        } else if (value instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) value;
            Set<Object> set = Sets.newHashSet();

            for (Map.Entry<?, ?> e : map.entrySet()) {
                set.add(e.getValue());
            }

            return set;
        } else if (value instanceof Collection<?>) {
            return Sets.newHashSet((Collection<?>) value);
        }

        throw new ClassCastException(String.format("The value for '%s' is not a set.", key));
    }

    /**
     * This method returns a value as a set; it will convert other collections
     * to sets by copying the elements. If the value is not a set, but is map,
     * this method will try to convert it using only the values; the keys are
     * ignored.
     *
     * @param key The key of the value.
     * @param itemClass The type of the individual elements.
     * @return The value as a set; always a copy of the set within.
     * @throws IllegalArgumentException If the key is not found.
     */
    public <T extends Storable> Set<T> getSet(String key, Class<T> itemClass) {
        Set<T> b = Sets.newHashSet();

        for (Object item : getList(key)) {
            b.add(convertValue(item, itemClass));
        }

        return b;
    }

    /**
     * This method returns a value from the map, just like get(), except that it
     * throws IllegalArgumentException if the key is not found.
     *
     * @param key
     * @return The value for the key.
     * @throws IllegalArgumentException If the key is not found.
     */
    public Object getObject(String key) {
        Object value = get(key);

        if (value == null) {
            throw new IllegalArgumentException(String.format("Key '%s' is not found in map file map.", key));
        }

        return value;
    }

    /**
     * This method retrieves a value, converting it to the indicated type if
     * required. If the value is present but not of the desired type, this
     * method will construct its return value using a single-argument
     * constructor whose argument is of the type we do have, typically String or
     * MapFileMap.
     *
     * @param <T> The type of the value to return.
     * @param valueClass The type of the value to return, again.
     * @return The value for the key.
     * @throws IllegalArgumentException if the key is not found.
     */
    public <T extends Storable> T getValue(String key, Class<T> valueClass) {
        Object value = get(key);

        if (value == null) {
            throw new IllegalArgumentException(String.format("Key '%s' is not found in map file map.", key));
        }

        return convertValue(value, valueClass);
    }

    /**
     * This method converts a value to a storable type; if necessary it will
     * invoke the storable type's constructor.
     *
     * @param <T> The desired type.
     * @param value The value to convert.
     * @param valueClass The desired type again.
     * @return The converted value; may be the same object as 'value'.
     */
    private static <T extends Storable> T convertValue(Object value, Class<T> valueClass) {
        if (valueClass.isInstance(value)) {
            return valueClass.cast(value);
        }

        try {
            Constructor<T> cons = valueClass.getConstructor(value.getClass());
            return cons.newInstance(value);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * This method extracts each key and value and copies them into the map
     * given; it will convert each one to the type indicated by 'valueClass' as
     * per getValue().
     *
     * @param <T> The type of the value to extract.
     * @param destination The map to be populated with keys and values.
     * @param valueClass The type of the value to extract, again.
     * @throws IllegalArgumentException if the key is not found.
     */
    public <T extends Storable> void copyInto(Map<? super String, ? super T> destination, Class<T> valueClass) {
        for (String key : keySet()) {
            destination.put(key, getValue(key, valueClass));
        }
    }

    ////////////////////////////////
    // Map Conversion
    //
    /**
     * This converts a map into a list of lines you can save. We can convert any
     * map, not just a MapTableMap. This method converts all keys and values to
     * strings, except values that can be sub-maps; they can be any kind of map
     * so long as they implement the interface.
     *
     * @param map The map to be converted.
     * @return The map converted to text, as lines.
     */
    public static List<String> getLinesFromMap(Map<?, ?> map) {
        Set<?> keys = ImmutableSortedSet.copyOf(map.keySet());
        ImmutableList.Builder<String> b = ImmutableList.builder();

        for (Object key : keys) {
            buildLinesFromMapEntry(b, key, map.get(key));
        }

        return b.build();
    }

    /**
     * This converts a collection into a list of lines you can save. These lines
     * look like a map's output, with the indices used as keys. You'll get a map
     * back when you read it back in.
     *
     * @param source The collection to be converted.
     * @return The collection converted to text, as lines.
     */
    public static List<String> getLinesFromCollection(Collection<?> source) {
        ImmutableList.Builder<String> b = ImmutableList.builder();

        int index = 0;
        for (Object element : source) {
            buildLinesFromMapEntry(b, index, element);
            ++index;
        }

        return b.build();
    }

    /**
     * This method populates the map with the entries encoded in the lines
     * given. These lines should be in the format getLinesFromMap() produces.
     *
     * Any existing entries in this map are removed.
     *
     * @param lines The lines to read into the map.
     */
    public void loadFromLines(List<String> lines) {
        clear();
        loadFromLines(lines.iterator());
    }

    /**
     * This method is the real implementation of loadFromLines(); it drains an
     * iterator to get the lines, which allows us to pass the iterator on to a
     * sub-map when required.
     *
     * @param iter The iterator to drain.
     */
    private void loadFromLines(Iterator<String> iter) {
        while (iter.hasNext()) {
            String line = iter.next();

            if (line.trim().equals("]")) {
                break;
            }

            int split = line.indexOf("=");

            if (split >= 0) {
                String key = unescape(line.substring(0, split));
                String value = line.substring(split + 1);

                if (value.trim().equals("[")) {
                    MapFileMap submap = new MapFileMap();
                    submap.loadFromLines(iter);
                    put(key, submap);
                } else {
                    put(key, unescape(value));
                }
            }
        }
    }

    /**
     * This method adds lines to a builder to generate the output for the map
     * file. This writes a single map entry, but that entry might have a map or
     * list as its value.
     */
    private static void buildLinesFromMapEntry(ImmutableList.Builder<String> destination, Object key, Object value) {
        if (value instanceof Storable) {
            value = ((Storable) value).toMap();
        }

        if (value instanceof Map<?, ?>) {
            Map<?, ?> submap = (Map<?, ?>) value;
            destination.add(String.format("%s=[", escape(key.toString())));
            destination.addAll(getLinesFromMap(submap));
            destination.add("]");
        } else if (value instanceof Collection<?>) {
            Collection<?> sublist = (Collection<?>) value;
            destination.add(String.format("%s=[", escape(key.toString())));
            destination.addAll(getLinesFromCollection(sublist));
            destination.add("]");
        } else {
            destination.add(String.format("%s=%s", escape(key.toString()), escape(value.toString())));
        }
    }

    /**
     * This method escapes a string so it can be placed in a file. We escape
     * using Minecraft's escape character '§'; We escape leading square
     * brackets, equal signs and line separators. We transform equal signs and
     * new lines so they won't be found by searches, to simply the parser.
     *
     * @param text The text to escape.
     * @return The 'safe' text that contains no dangerous characters.
     */
    private static String escape(String text) {
        if (text.equals("[")) {
            return "§[";
        }

        return text.
                replace("§", "§§").
                replace(NEW_LINE, "§n").
                replace("=", "§-");
    }

    /**
     * This method reverses the effect of 'escape'. It recognizes the §n and §-
     * escape sequences for line separators and equal signs, and any other § is
     * just removed, except for doubled up '§§' escapes.
     *
     * @param text The text to unescape.
     * @return The normal text restored.
     */
    private static String unescape(String text) {
        StringBuilder b = new StringBuilder(text);

        for (int i = 0; i < b.length() - 1; ++i) {
            if (b.charAt(i) == '\\') {
                char next = b.charAt(i + 1);
                if (next == 'n') {
                    b.replace(i, 2, NEW_LINE);
                    i += NEW_LINE.length() - 1;
                } else if (next == '-') {
                    b.replace(i, 2, "=");
                } else {
                    b.replace(i, 1, "");
                }
            }
        }

        return b.toString();
    }
    ////////////////////////////////
    // File Access
    public static final String NEW_LINE = System.getProperty("line.separator");

    /**
     * This method reads a MapFileMap from a file; the resulting map contains
     * only strings and other MapFileMaps. All IOExceptions are wrapped as
     * RuntimeExceptions.
     *
     * This method reads the files created by write().
     *
     * @param file The file to read from.
     * @return The new map, read from the file.
     */
    public static MapFileMap read(File file) {
        try {
            MapFileMap map = new MapFileMap();
            map.loadFromLines(Files.readLines(file, Charsets.UTF_8));
            return map;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * This method writes the content of this map to a file. This writes the
     * lines produces by getLinesFromMap() to a UTF8 text file. All IOExceptions
     * are wrapped as RuntimeExceptions.
     *
     * The map need not be a MapFileMap, but all keys are converted to strings,
     * and all values that are not maps are also so converted. Values can
     * contain maps, which are preserved.
     *
     * @param file The file to write to.
     * @param map The map to encode.
     */
    public static void write(File file, Map<?, ?> map) {
        try {
            String text = Joiner.on(NEW_LINE).join(getLinesFromMap(map));
            Files.write(text, file, Charsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * This interface may be exposed by classes that can be saved inside a
     * MapFileMap; they must convert themselves to maps, which are then stored.
     * We do not save the type of the object at all; you are expected to know
     * what type it is and to call getValue() with that type.
     *
     * Implementations of this interface will typically have a public
     * constructor that takes MapFileMap to restore the object from the file.
     */
    public interface Storable {

        /**
         * This method returns a map we can save to a file. The MapFileMAp
         * implementation does not retain this map, but merely writes it out. we
         * can therefore return a map we already have. If the value type itself
         * implements Storable, we can effectively serialize a complex object
         * tree this way.
         *
         * @return A map containing the data to save to represent this object.
         */
        Map<?, ?> toMap();
    }

    ////////////////////////////////
    // Implementation
    /**
     * This method converts an object to an integer; if it's not already a
     * number, this will convert it to a string and parse it.
     *
     * @param value The object to convert.
     * @return The same value, but now an integer.
     */
    private static int convertToInteger(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        } else {
            return Integer.parseInt(value.toString());
        }
    }
}
