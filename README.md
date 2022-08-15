## Features
* Ability to load the entire config into a class with public final fields
* Support for getting a property by path `"x.y.z"`
* Config based on a backing map, `MapConfig`
	* Config based on the system properties, `SystemPropertyConfig`
	* Config based on the environment variables, `EnvironmentVariableConfig`
* Ability to easily merge different configs, `SystemPropertyConfig.get().merge(MyConfig.get())`
* Template variables, `"x": "${y} z"`

## TODO
[ ] Support for @Nullable and @NonNull to change the generated default values  
[ ] Support for custom value converters with a new IConverter interface  
[ ] Support for @Require in the interface based configs  
[ ] Support for @Convert in the interface based configs, this would be used to have a single special converter for a particular property, the method would be private, accept the input value (of any type) and return the new value  

## How does it work?
There are two primary components
1. The IConfig, it is basically equivalent to a map but with good type conversions and methods to support most of the wanted conversions, like, `getLong`, `getList` and `getMap`, this stores the values as they are loaded and converts all of the values dynamically, this is the most flexible.
2. The result config, this could, for instance, be an interface (see example below) where all of the wanted config properties and their types are known, this will store all of the values already converted to the wanted types meaning you don't have to sacrifice any performance at all.

## More documentation will come in the future, if you are interested in using this feel free to open an issue and I can help you through it

## Example

All of the methods in an interface based config (excluding methods marked with `@Ignore`) are only called once, the value is then stored and retrieved on any future calls
### Config Format
```Java
/* You can set the property naming schema for your config */
@Config(naming=Naming.SNAKE_CASE)
public interface MyConfig {
	
	/* Get the backing config */
	@Identity
	public IConfig get();
	
	public String getToken();
	
	/* You can have nested configs */
	@Name("mongodb")
	public MongoDB getMongoDB();
	
	public interface MongoDB {
		
		public List<Host> getHosts();
		
		public interface Host {
			
			/* You can set default values */
			public default String getIp() {
				return "localhost";
			}
			
			public default int getPort() {
				return 27017;
			}
		}
		
		public Credentials getCredentials();
		
		public interface Credentials {
			
			public String getUserName();
			public String getPassword();
			public default String getUserDatabase() {
				return "admin";
			}
		}
		
		/* You can compute methods to create custom values, this can use other methods too */
		@Computed
		public default String getUserName() {
			return this.getCredentials().getUserName();
		}
		
		@Computed
		public default String getPassword() {
			return this.getCredentials().getPassword();
		}
		
		@Computed
		public default String getUserDatabase() {
			return this.getCredentials().getUserDatabase();
		}
		
		/* You can use a separate name for the config property */ 
		@Name("database.name")
		public default String getDatabaseName() {
			return "application";
		}
	}
}
```

### Loading the config
Here is an example of loading the above config from all the files in the `config` directory parsed from JSON using the `org.json` library, this includes environment variables and all system properties starting with `my.application.`.
```Java
public static final String SYSTEM_PROPERTY_PREFIX = "my.application";

public static Config loadConfig() throws IOException {
	List<IConfig> configs = new ArrayList<>();
	configs.add(ConfigFactory.environmentVariables());
	
	File[] configFiles = new File("./config/").listFiles();
	for(File file : configFiles) {
		JSONObject data;
		try(FileInputStream stream = new FileInputStream(file)) {
			data = new JSONObject(new JSONTokener(stream));
		}
		
		configs.add(ConfigFactory.fromMap(data.toMap()));
	}
	
	configs.add(ConfigFactory.systemProperties(SYSTEM_PROPERTY_PREFIX));
	
	IConfig config = ConfigFactory.empty()
		.merge(configs.toArray(new IConfig[0]))
		.resolve();
	
	return ConfigFactory.create(config, MyConfig.class);
}
```

### Alternative config format
Here is an alternative to the interface implementation, this is an experimental proof of concept but it works well and is in a pretty tiny format which means you can get a better overview of the entirety, if you like this format it's an option!
```Java
/* You can set the property naming schema for your config */
@Config(naming=Naming.SNAKE_CASE)
public class MyConfig extends AbstractFieldConfig {
	
	/* Get the backing config */
	@Identity
	public final IConfig config = with();
	
	/* You can require properties to be set */
	public final String token = require();
	
	/* You can have nested configs */
	public final MongoDB mongodb = with();
	
	public static class MongoDB extends AbstractFieldConfig {
		
		public final List<Host> hosts = with();
		
		public static class Host extends AbstractFieldConfig {
			
			/* You can set default values inside of with */
			public final String ip = with("localhost");
			public final int port = with(27017);
			
		}
		
		public final Credentials credentials = with();
		
		public static class Credentials extends AbstractFieldConfig {
			
			public final String userName = with();
			public final String password = with();
			public final String userDatabase = with("admin");
			
		}
		
		/* You can ignore fields and set custom values */
		@Ignore
		public final String userName = this.credentials.userName;
		
		@Ignore
		public final String password = this.credentials.password;
		
		@Ignore
		public final String userDatabase = this.credentials.userDatabase;
		
		/* You can use a separate name for the config property */ 
		@Name("database.name")
		public final String databaseName = with("application");
	}
}
```