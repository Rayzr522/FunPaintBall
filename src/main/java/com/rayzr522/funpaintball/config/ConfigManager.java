
package com.rayzr522.funpaintball.config;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import com.rayzr522.funpaintball.config.handlers.LocationSerializer;
import com.rayzr522.funpaintball.config.handlers.VectorSerializer;
import com.rayzr522.funpaintball.config.handlers.WorldSerializer;
import com.rayzr522.funpaintball.util.ArrayUtils;
import com.rayzr522.funpaintball.util.Reflection;

public class ConfigManager {

	private static Map<Class<? extends Object>, ISerializationHandler<? extends Object>> serializationHandlers = new HashMap<>();

	public static void registerSerializationHandler(Class<?> clazz, ISerializationHandler<?> handler) {

		if (serializationHandlers.containsKey(clazz)) {
			System.out.println("WARNING: Registering serialization handler for class '" + clazz.getCanonicalName() + "', but a handler was already present.");
		}
		serializationHandlers.put(clazz, handler);

	}

	static {

		registerSerializationHandler(Vector.class, new VectorSerializer());
		registerSerializationHandler(World.class, new WorldSerializer());
		registerSerializationHandler(Location.class, new LocationSerializer());

	}

	@SuppressWarnings("unused")
	private JavaPlugin	plugin;
	private File		dataFolder;

	public ConfigManager(JavaPlugin plugin) {

		this.plugin = plugin;
		this.dataFolder = plugin.getDataFolder();

		ensureFolderExists(dataFolder);

	}

	public boolean ensureFileExists(File file) {

		if (!file.exists()) {
			try {
				file.createNewFile();
				return false;
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}
		}

		return true;

	}

	public boolean ensureFolderExists(File file) {

		if (!file.exists()) {
			file.mkdir();
			return false;
		}

		return true;

	}

	/**
	 * If the config file already exists it is not overwritten
	 * 
	 * @param path
	 *            the path of the file
	 * @return A new config file
	 */
	public YamlConfiguration createConfig(String path) {

		File file = new File(dataFolder, path);
		ensureFileExists(file);

		return YamlConfiguration.loadConfiguration(file);

	}

	public void saveConfig(YamlConfiguration config, File file) {
		try {
			config.save(file);
		} catch (Exception e) {
			System.err.println("Failed to save config file");
		}
	}

	public void saveConfig(YamlConfiguration config, String file) {
		try {
			createConfig(file);
			config.save(getFile(file));
		} catch (Exception e) {
			System.err.println("Failed to save config file");
		}
	}

	@SuppressWarnings("unchecked")
	public <T extends Object> T load(Class<T> clazz, String path) {

		if (!getFile(path).exists()) { return null; }

		YamlConfiguration config = createConfig(path);

		Map<String, Object> data = convertToMap(config);

		return (T) deserialize(clazz, data);

	}

	@SuppressWarnings("unchecked")
	public <T extends Object> T load(Class<T> clazz, YamlConfiguration config) {

		Map<String, Object> data = convertToMap(config);

		return (T) deserialize(clazz, data);

	}

	@SuppressWarnings("unchecked")
	public <T extends Object> T load(Class<T> clazz, ConfigurationSection section) {

		Map<String, Object> data = convertToMap(section);

		return (T) deserialize(clazz, data);

	}

	public void save(Object o, String path) {

		YamlConfiguration config = createConfig(path);

		save(o, config);

		saveConfig(config, path);

	}

	public void save(Object o, YamlConfiguration config) {

		Map<String, Object> map = serialize(o);

		if (map == null) { return; }

		saveToConfig(config, map);

	}

	public void save(Object o, YamlConfiguration config, String path) {

		save(o, config);

		saveConfig(config, path);

	}

	public void save(Object o, ConfigurationSection section) {

		Map<String, Object> map = serialize(o);

		if (map == null) { return; }

		for (Entry<String, Object> entry : map.entrySet()) {

			section.set(entry.getKey(), entry.getValue());

		}

	}

	public static Map<String, Object> serialize(Object o) {

		if (!Reflection.hasInterface(o, ISerializable.class)) {
			System.err.println("Attempted to serialize a class that does not implement Serializable!");
			System.err.println("Invalid class: '" + o.getClass().getCanonicalName() + "'");
			System.err.println("Interfaces: " + ArrayUtils.concat(o.getClass().getInterfaces(), ", "));
			return null;
		}

		List<Field> fields = Reflection.getFieldsWithAnnotation(o.getClass(), Serialized.class);

		Map<String, Object> map = new HashMap<String, Object>();

		for (Field field : fields) {

			try {

				// Save the state of the field
				boolean accessible = field.isAccessible();
				field.setAccessible(true);

				// Check if it's another Serializable
				if (Reflection.hasInterface(field.getType(), ISerializable.class)) {
					ISerializable serializable = (ISerializable) field.get(o);
					if (serializable != null) {
						serializable.onPreSerialize();
						map.put(field.getName(), serialize(serializable));
					} else {
						map.put(field.getName(), null);
					}
				} else if (serializationHandlers.containsKey(field.getType())) {

					// Get the handler for this type
					ISerializationHandler<? extends Object> handler = serializationHandlers.get(field.getType());
					try {
						map.put(field.getName(), handler._serialize(field.get(o)));
					} catch (ClassCastException e) {
						System.err.println("SerializationHandler '" + handler.getClass().getCanonicalName() + "' encountered an invalid type while trying to load data for field '" + field.getName() + "'");
						e.printStackTrace();
					} catch (Exception e) {

					}

				} else {

					// Insert the raw value (won't always work)
					map.put(field.getName(), field.get(o));

				}

				field.setAccessible(accessible);

			} catch (IllegalArgumentException e) {

				e.printStackTrace();

			} catch (IllegalAccessException e) {

				e.printStackTrace();

			} catch (StackOverflowError e) {

				System.err.println("Data serializer caught in infinite loop while trying to serialize an object of type '" + o.getClass().getCanonicalName() + "'!");
				e.printStackTrace();

			}

		}

		return map;

	}

	@SuppressWarnings("unchecked")
	public static Object deserialize(Class<? extends Object> clazz, Map<String, Object> data) {

		if (!ISerializable.class.isAssignableFrom(clazz)) {
			System.err.println("Attempted to deserialize to a non-serializable class!!");
			return null;
		}

		Object o;
		try {
			o = clazz.newInstance();
		} catch (Exception e) {
			System.err.println("Could not instantiate an object of type '" + clazz.getCanonicalName() + "'");
			System.err.println("Classes implementing Serializable should not have a constructor, instead they should use onDeserialize.");
			e.printStackTrace();
			return null;
		}

		List<Field> fields = Reflection.getFieldsWithAnnotation(o.getClass(), Serialized.class);

		for (Field field : fields) {
			if (!data.containsKey(field.getName())) {
				continue;
			}
			try {

				boolean map = (data.get(field.getName()) instanceof Map<?, ?>);

				if (Reflection.hasInterface(field.getType(), ISerializable.class)) {

					if (!map) {

						System.err.println("Expected a Map for field '" + field.getName() + "' in '" + clazz.getCanonicalName() + "', however an instance of '" + data.get(field.getName()).getClass().getCanonicalName() + "' was found!");
						return null;

					}

					ISerializable deserialized = (ISerializable) deserialize(field.getType(), (Map<String, Object>) data.get(field.getName()));
					// If the object could not be deserialized then return null
					if (deserialized == null) { return null; }
					deserialized.onDeserialize();
					Reflection.setValue(field, o, deserialized);

				} else if (serializationHandlers.containsKey(field.getType())) {

					// If this isn't a map then it will error
					if (!map) {

						System.err.println("Expected a Map for field '" + field.getName() + "' in '" + clazz.getCanonicalName() + "', however an instance of '" + data.get(field.getName()).getClass().getCanonicalName() + "' was found!");
						return null;

					}

					// Get the handler for this type
					ISerializationHandler<?> handler = serializationHandlers.get(field.getType());
					// Attempt to set the value to the deserialized value. This
					// will error if data.get() does not return a map

					try {
						Reflection.setValue(field, o, handler.deserialize((Map<String, Object>) data.get(field.getName())));
					} catch (Exception e) {
						System.err.println("Tried to use serialization handler for type '" + field.getType().getCanonicalName() + "', but an error occured:");
						e.printStackTrace();
					}

				} else {

					// Just set the raw value
					Reflection.setValue(field, o, data.get(field.getName()));

				}
			} catch (Exception e) {

				// Do various things if specified in an OnFail annotation
				if (field.isAnnotationPresent(OnFail.class)) {

					OnFail fail = field.getAnnotation(OnFail.class);
					switch (fail.value()) {

					case USE_DEFAULT:
						break;
					case CANCEL_LOAD:
						System.err.println("Failed to load field '" + field.getName() + "' in class '" + o.getClass().getCanonicalName() + "'");
						System.err.println("OnFail = CANCEL_LOAD, cancelling load");
						return null;
					case CONSOLE_ERR:
						System.err.println("Failed to load field '" + field.getName() + "' in class '" + o.getClass().getCanonicalName() + "'");
						break;
					default:
						break;

					}

				}

			}

		}

		return o;

	}

	public File getFile(String path) {
		return new File(dataFolder + File.separator + path);
	}

	@SuppressWarnings("unchecked")
	public static boolean saveToConfig(YamlConfiguration config, Map<String, Object> map) {

		if (config == null || map == null) { return false; }

		for (Entry<String, Object> entry : map.entrySet()) {

			if (entry.getValue() != null && Map.class.isAssignableFrom(entry.getValue().getClass())) {
				saveToConfig(config.createSection(entry.getKey()), (Map<String, Object>) entry.getValue());
			} else {
				config.set(entry.getKey(), entry.getValue());
			}

		}

		return true;

	}

	@SuppressWarnings("unchecked")
	public static void saveToConfig(ConfigurationSection section, Map<String, Object> map) {

		if (section == null || map == null) { return; }

		for (Entry<String, Object> entry : map.entrySet()) {
			if (Map.class.isAssignableFrom(entry.getValue().getClass())) {
				saveToConfig(section.createSection(entry.getKey()), (Map<String, Object>) entry.getValue());
			} else {
				section.set(entry.getKey(), entry.getValue());
			}
		}

	}

	public static Map<String, Object> convertToMap(ConfigurationSection section) {

		Map<String, Object> map = new HashMap<String, Object>();

		for (String key : section.getKeys(false)) {

			if (section.isConfigurationSection(key)) {
				map.put(key, convertToMap(section.getConfigurationSection(key)));
			} else {
				map.put(key, section.get(key));
			}

		}

		return map;

	}

}
