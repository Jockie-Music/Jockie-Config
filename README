## Features
* Ability to load the entire config into a class with public final fields
* Support for getting a property by path `"x.y.z"`
* Config based on a backing map, `MapConfig`
	* Config based on the system properties, `SystemPropertyConfig`
	* Config based on the environment variables, `EnvironmentVariableConfig`
* Ability to easily merge different configs, `SystemPropertyConfig.get().merge(MyConfig.get())`
* Template variables, `"x": "${y} z"`

## More documentation will come in the future, if you are interested in using this feel free to open an issue and I can help you through it

## Example
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

here's an example of loading the above config from all the files in the `config` directory
parsed from JSON using the `org.json` library, this includes environment variables and all
system properties starting with `my.application.`.
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