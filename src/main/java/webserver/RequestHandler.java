package webserver;

import db.MemoryUserRepository;
import model.User;

import java.io.*;
import java.lang.reflect.Array;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import static db.MemoryUserRepository.getInstance;
import static http.util.HttpRequestUtils.parseQueryParameter;

public class RequestHandler implements Runnable{
    Socket connection;
    private static final Logger log = Logger.getLogger(RequestHandler.class.getName());
    private static final String BASE_URL = "./webapp";
    private static final String HOME_URL = "/index.html";

    public RequestHandler(Socket connection) {
        this.connection = connection;
    }

    @Override
    public void run() {
        log.log(Level.INFO, "New Client Connect! Connected IP : " + connection.getInetAddress() + ", Port : " + connection.getPort());
        try (InputStream in = connection.getInputStream(); OutputStream out = connection.getOutputStream()){
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            DataOutputStream dos = new DataOutputStream(out);

            byte[] body = new byte[0];
            int requestContentLength = 0;

            // InputStream에서 요청을 읽어와 StartLine 파싱
            String startLine = br.readLine();
            String[] startLines = startLine.split(" ");
            String requestMethod = startLines[0];
            String requestUrl = startLines[1];

            // Header 파싱
            while (true) {
                final String line = br.readLine();
                // blank line 만나면 requestBody 시작되므로 break
                if (line.equals("")) {
                    break;
                }

                if (line.startsWith("Content-Length")) {
                    requestContentLength = Integer.parseInt(line.split(": ")[1]);
                }
            }

            // 1 기본값(홈) url 설정
            if (requestMethod.equals("GET") && requestUrl.equals("/")) {
                body = Files.readAllBytes(Paths.get(BASE_URL + HOME_URL));
            }

            // 1 .html로 끝나는 url의 경우
            if (requestMethod.equals("GET") && requestUrl.endsWith(".html")) {
                body = Files.readAllBytes(Paths.get(BASE_URL + requestUrl));
            }

            // 2 GET 방식으로 회원가입
            if (requestMethod.equals("GET") && requestUrl.startsWith("/user/signup?")) {
                // 쿼리 스트링 기준으로 파싱
                String[] parsedUrl = requestUrl.split("[?]");
                Map<String, String> queryStringMap = parseQueryParameter(parsedUrl[1]);

                // URL 인코딩된 값을 디코딩
                for (Map.Entry<String, String> entry : queryStringMap.entrySet()){
                    try {
                        String key = entry.getKey();
                        // 디코딩
                        String decodedValue = java.net.URLDecoder.decode(entry.getValue(),"UTF-8");
                        queryStringMap.put(key, decodedValue);
                    } catch (java.io.UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                }

                String userId = queryStringMap.get("userId");
                String name = queryStringMap.get("name");
                String password = queryStringMap.get("password");
                String email = queryStringMap.get("email");

                User user = new User(userId, password, name, email);
                System.out.println(userId+name+password+email);

                // MemoryUserRepository 객체에 User 저장 및 확인
                MemoryUserRepository userRepository = getInstance();
                userRepository.addUser(user);

                Collection<User> allUsers = userRepository.findAll();
                for (User storedUser: allUsers) {
                    System.out.println("userId: " + storedUser.getUserId());
                    System.out.println("name: " + storedUser.getName());
                    System.out.println("password: " + storedUser.getPassword());
                    System.out.println("email: " + storedUser.getEmail());
                    System.out.println();
                }

            }

            response200Header(dos, body.length);
            responseBody(dos, body);

        } catch (IOException e) {
            log.log(Level.SEVERE,e.getMessage());
        }
    }

    private void response200Header(DataOutputStream dos, int lengthOfBodyContent) {
        try {
            dos.writeBytes("HTTP/1.1 200 OK \r\n");
            dos.writeBytes("Content-Type: text/html;charset=utf-8\r\n");
            dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.log(Level.SEVERE, e.getMessage());
        }
    }

    private void responseBody(DataOutputStream dos, byte[] body) {
        try {
            dos.write(body, 0, body.length);
            dos.flush();
        } catch (IOException e) {
            log.log(Level.SEVERE, e.getMessage());
        }
    }


}