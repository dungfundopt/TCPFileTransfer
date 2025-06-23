Từ thư mục tcp:
Build Project: mvn clean install
1. Chạy server:
mvn exec:java -pl server
mvn javafx:run -pl client

## System Requirements

### Client
- Java Runtime Environment (JRE) 11 or higher
- JavaFX runtime
- Minimum 4GB RAM recommended
- Storage space for encrypted files

### Server
- Java Development Kit (JDK) 11 or higher
- Minimum 8GB RAM recommended
- Sufficient storage for user files
- Network connectivity

## Installation

1. Clone the repository:
```bash
git clone [repository-url]
```

2. Build the project:
```bash
mvn clean install
```

3. Start the server:
```bash
mvn exec:java "-Dexec.mainClass=com.example.server.Server"
```

4. Launch the client:
```bash
mvn exec:java "-Dexec.mainClass=com.example.client.App"
```