import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

public class Server {
  static final Map<Integer, String> Status = Map.of(
      200, "ok",
      204, "no content",
      400, "bad request",
      404, "not found",
      415, "unsupported media type",
      500, "internal server error");
  static final Set<String> contentTypes = new HashSet<>(Arrays.asList("text/plain", "text/html", "*/*", "text/*"));
  static final Set<String> requestTypes = new HashSet<>(Arrays.asList("GET", "PUT", "POST", "DELETE"));
  static final int PORT = 8080;

  public static void main(String[] args) {
    try (ServerSocket server = new ServerSocket(PORT);) {
      while (true) {
        Socket client = server.accept();
        System.out.println("Client connected:::::::::::::::::::::::::::\n");
        handleClient(client);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static void handleClient(Socket client) {
    try (
        PrintWriter out = new PrintWriter(client.getOutputStream(), true);
        BufferedReader in = new BufferedReader(
            new InputStreamReader(client.getInputStream()), 1);) {
      String firstLine = in.readLine();
      String[] request = firstLine.split(" ");
      System.out.println(firstLine);

      if (request.length < 3) {
        out.println(generateResponse(400));
        return;
      }
      String requestType = request[0].toUpperCase();
      String filepath = request[1];
      String rfc = request[2].toUpperCase();
      String acceptType = "*/*";
      StringBuilder bodyBuilder = new StringBuilder();
      if (rfc.equals("HTTP/1.1") && requestTypes.contains(requestType) && contentTypes.contains(acceptType)) {
        int contentLength = 0;
        for (String inputLine = in.readLine(); inputLine != null; inputLine = in.readLine()) {
          System.out.println(inputLine);
          if (inputLine.isEmpty()) {
            break;
          }

          String[] header = inputLine.split(":");
          String headerType = header[0].trim().toLowerCase();
          String headerValue = header[1].trim().toLowerCase();
          switch (headerType) {
            case "accept":
              acceptType = headerValue;
              break;
            case "content-length":
              contentLength = Integer.valueOf(headerValue);
              break;
          }
        }
        while (contentLength > 0) {
          char c = (char) in.read();
          System.out.print(c);
          bodyBuilder.append(c);
          contentLength--;
        }
        System.out.println('\n');
      }

      String response;
      switch (requestType) {
        case "GET":
          response = handleGet(filepath, acceptType);
          break;
        case "PUT":
          response = handlePut(filepath, acceptType, bodyBuilder.toString());
          break;
        case "POST":
          response = handlePost(filepath, bodyBuilder.toString());
          break;
        case "DELETE":
          response = handleDelete(filepath);
          break;
        default:
          response = generateResponse(400);
      }
      System.out.println(response);
      out.println(response);

      System.out.println("Client handled:::::::::::::::::::::::::::::");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static String handleGet(String filepath, String acceptType) throws IOException {
    if (filepath.length() < 2) {
      return generateResponse(404);
    }
    filepath = filepath.substring(1, filepath.length());
    String[] filepathSplit = filepath.split("\\.");
    String extension = filepathSplit[filepathSplit.length - 1];
    String contentType = "";
    if (extension.equals("txt")) {
      contentType = "text/plain";
    } else if (extension.equals("html")) {
      contentType = "text/html";
    }
    if (!typeMatches(contentType, acceptType)) {
      return generateResponse(415);
    }

    if (contentType.isEmpty() || !Files.exists(Paths.get(filepath))) {
      return generateResponse(404);
    }
    BufferedReader reader = new BufferedReader(new FileReader(filepath));
    StringBuilder stringBuilder = new StringBuilder();
    String line = null;
    String ls = System.getProperty("line.separator");
    while ((line = reader.readLine()) != null) {
      stringBuilder.append(line);
      stringBuilder.append(ls);
    }
    // delete the last new line separator
    stringBuilder.deleteCharAt(stringBuilder.length() - 1);
    reader.close();

    String content = stringBuilder.toString();
    System.out.println(content);
    return generateResponse(200, content.length(), content, contentType);
  }

  private static String handlePut(String filepath, String acceptType, String body)
      throws IOException {
    if (filepath.length() < 2) {
      return generateResponse(404);
    }
    filepath = filepath.substring(1, filepath.length());
    String[] filepathSplit = filepath.split("\\.");
    String extension = filepathSplit[filepathSplit.length - 1];
    String contentType = "";
    if (extension.equals("txt")) {
      contentType = "text/plain";
    } else if (extension.equals("html")) {
      contentType = "text/html";
    }
    if (contentType.isEmpty() || !Files.exists(Paths.get(filepath))) {
      return generateResponse(404);
    }

    Files.write(
        Paths.get(filepath),
        body.getBytes(),
        StandardOpenOption.APPEND);
    return generateResponse(200);
  }

  private static String handlePost(String filepath, String body) throws IOException {
    if (filepath.length() < 2) {
      return generateResponse(204);
    }
    filepath = filepath.substring(1, filepath.length());
    String[] filepathSplit = filepath.split("\\.");
    String extension = filepathSplit[filepathSplit.length - 1];
    String contentType = "";
    if (extension.equals("txt")) {
      contentType = "text/plain";
    } else if (extension.equals("html")) {
      contentType = "text/html";
    }
    if (contentType.isEmpty()) {
      return generateResponse(204);
    }

    Path path = Paths.get(filepath);
    if (Files.exists(path)) {
      Files.delete(path);
    }

    String[] filePathDirSplit = filepath.split("/");
    String filename = filePathDirSplit[filePathDirSplit.length - 1];
    String dirs = filepath.substring(0, filepath.length() - filename.length() - 1);
    System.out.println(dirs);
    Files.createDirectories(Paths.get(dirs));
    Files.createFile(path);
    Files.write(
        path,
        body.getBytes(),
        StandardOpenOption.APPEND);

    return generateResponse(200);
  }

  private static String handleDelete(String filepath) throws IOException {
    if (filepath.length() < 2) {
      return generateResponse(204);
    }
    filepath = filepath.substring(1, filepath.length());
    String[] filepathSplit = filepath.split("\\.");
    String extension = filepathSplit[filepathSplit.length - 1];
    String contentType = "";
    if (extension.equals("txt")) {
      contentType = "text/plain";
    } else if (extension.equals("html")) {
      contentType = "text/html";
    }
    if (contentType.isEmpty() || !Files.exists(Paths.get(filepath))) {
      return generateResponse(204);
    }

    Files.delete(Paths.get(filepath));

    return generateResponse(200);
  }

  private static String generateResponse(int code) {
    return String.format("HTTP/1.1 %d %s\r\n\r\n", code, Status.get(code));
  }

  private static String generateResponse(int code, int contentLength, String content, String contentType) {
    return String.format("HTTP/1.1 %d %s\r\ncontent-length: %d\r\ncontent-type: %s\r\n\r\n%s", code, Status.get(code),
        contentLength, contentType, content);
  }

  private static boolean typeMatches(String my, String other) {
    if (my.equals(other)) {
      return true;
    } else if (other.equals("text/*")) {
      return true;
    } else if (other.equals("*/*")) {
      return true;
    }
    return false;
  }
}