package jacamo.platform;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import cartago.ArtifactId;
import cartago.ArtifactInfo;
import cartago.ArtifactObsProperty;
import cartago.CartagoException;
import cartago.CartagoService;
import jacamo.util.Config;
import ora4mas.nopl.WebInterface;

public class EnvironmentWebInspector implements Platform {
    
    boolean webOn = true;

    @Override
    public void init(String[] args) {
        if (args.length == 1) {
            Config.get().setProperty(Config.START_WEB_EI, args[0]);
            webOn = !"false".equals(args[0]);
        }
    }

    @Override
    public void start() {
        if (webOn) {
            startHttpServer();
            registerWorkspace(CartagoService.MAIN_WSP_NAME);
        }
    }
    
    public static String getArtHtml(String wId, ArtifactInfo info) {
        StringBuilder out = new StringBuilder("<html>");
        //out.append("<head><meta http-equiv=\"refresh\" content=\""+refreshInterval+"\"></head>");
        out.append("<span style=\"color: red; font-family: arial\"><font size=\"+2\">");
        out.append("Inspection of artifact <b>"+info.getId().getName()+"</b> in workspace "+wId+"</font></span>");
        out.append("<table border=0 cellspacing=3 cellpadding=6 style='font-family:verdana'>");
        for (ArtifactObsProperty op: info.getObsProperties()) {
            StringBuilder vls = new StringBuilder();
            String v = "";
            for (Object vl: op.getValues()) {
                vls.append(v+vl);
                v = ",";
            }
            out.append("<tr><td>"+op.getName()+"</td><td>"+vls+"</td></tr>");
        }
        out.append("</table>");
        /*out.append("</br>Operations:<ul>");
        for (OperationInfo op: info.getOngoingOp()) {
            out.append("<li>"+op.toString()+"</li>");
        }
        for (OpDescriptor op: info.getOperations()) {
            out.append("<li>"+op.getOp().getName()+"</li>");
        }
        out.append("</ul>");*/
        out.append("</html>");
        return out.toString();
    }

    /** Http Server for GUI */

    static HttpServer httpServer = null;
    static int        httpServerPort = 3273;
    static String     httpServerURL = "http://localhost:"+httpServerPort;

    static Set<String> wrkps = new HashSet<>();

    static Set<String> hidenArts = new HashSet<>( Arrays.asList(new String[] {
        "node",
        "console",
        "blackboard",
        "workspace",
        "manrepo",
    }));

    public static synchronized String startHttpServer()  {
        if (httpServer == null) {
            try {
                httpServer = HttpServer.create(new InetSocketAddress(httpServerPort), 0);
                httpServer.setExecutor(Executors.newCachedThreadPool());
                registerRootBrowserView();
                registerWksListBrowserView();
                httpServer.start();
                httpServerURL = "http://"+InetAddress.getLocalHost().getHostAddress()+":"+httpServerPort;
                System.out.println("CArtAgO Http Server running on "+httpServerURL);
            } catch (BindException e) {
                httpServerPort++;
                return startHttpServer();
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
        return httpServerURL;
    }

    static void registerRootBrowserView() {
        if (httpServer == null)
            return;
        try {
            httpServer.createContext("/", new HttpHandler() {
                public void handle(HttpExchange exchange) throws IOException {
                    String requestMethod = exchange.getRequestMethod();
                    Headers responseHeaders = exchange.getResponseHeaders();
                    responseHeaders.set("Content-Type", "text/html");
                    exchange.sendResponseHeaders(200, 0);
                    OutputStream responseBody = exchange.getResponseBody();

                    if (requestMethod.equalsIgnoreCase("GET")) {
                        String path = exchange.getRequestURI().getPath();
                        StringWriter so = new StringWriter();

                        if (path.length() < 2) { // it is the root
                            so.append("<html><head><title>CArtAgO Web View</title></head><body>");
                            so.append("<iframe width=\"20%\" height=\"100%\" align=left src=\"/indexarts\" border=5 frameborder=0 ></iframe>");
                            so.append("<iframe width=\"78%\" height=\"100%\" align=left src=\"/arts\" name=\"arts\" border=5 frameborder=0></iframe>");
                            so.append("</body></html>");
                        }
                        responseBody.write(so.toString().getBytes());
                    }
                    responseBody.close();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void registerWorkspace(String w) {
        wrkps.add(w);
        registerWksBrowserView(w);
    }

    private static void registerWksListBrowserView() {
        if (httpServer == null)
            return;
        try {
            httpServer.createContext("/indexarts", new HttpHandler() {
                public void handle(HttpExchange exchange) throws IOException {
                    String requestMethod = exchange.getRequestMethod();
                    Headers responseHeaders = exchange.getResponseHeaders();
                    responseHeaders.set("Content-Type", "text/html");
                    exchange.sendResponseHeaders(200, 0);
                    OutputStream responseBody = exchange.getResponseBody();

                    if (requestMethod.equalsIgnoreCase("GET")) {
                        //responseBody.write(("<html><head><title>CArtAgO (list of artifacts)</title><meta http-equiv=\"refresh\" content=\""+refreshInterval+"\" ></head><body>").getBytes());
                        responseBody.write(("<html><head><title>CArtAgO (list of artifacts)</title></head><body>").getBytes());
                        responseBody.write(("<font size=\"+2\"><span style='color: red; font-family: arial;'>workspaces</span></font><br/>").getBytes());
                        StringWriter out  = new StringWriter();
                        for (String wname: wrkps) {
                            try {
                                out.append("<br/><scan style='color: red; font-family: arial;'><a href='/"+wname+"/img.svg' target='arts'>"+wname+"</a></scan> <br/>");
                                for (ArtifactId aid: CartagoService.getController(wname).getCurrentArtifacts()) {
                                    if (hidenArts.contains(aid.getName()))
                                        continue;
                                    if (aid.getName().endsWith("-body"))
                                        continue;
                                    String addr = wname+"/"+aid.getName();
                                    out.append(" - <a href=\""+addr+"\" target=\"arts\" style=\"font-family: arial; text-decoration: none\">"+aid.getName()+"</a><br/>");
                                }
                            } catch (CartagoException e) {
                                e.printStackTrace();
                            }
                        }
                        responseBody.write( out.toString().getBytes());
                    }
                    responseBody.write("<hr/>by <a href=\"http://cartago.sourceforge.net\" target=\"_blank\">CArtAgO</a>".getBytes());
                    responseBody.write("</body></html>".getBytes());
                    responseBody.close();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void registerWksBrowserView(final String id) {
        if (httpServer == null)
            return;
        try {
            httpServer.createContext("/"+id, new HttpHandler() {
                public void handle(HttpExchange exchange) throws IOException {
                    String requestMethod = exchange.getRequestMethod();
                    Headers responseHeaders = exchange.getResponseHeaders();
                    exchange.sendResponseHeaders(200, 0);
                    OutputStream responseBody = exchange.getResponseBody();
                    if (requestMethod.equalsIgnoreCase("GET")) {
                        if (exchange.getRequestURI().getPath().endsWith("svg")) {
                            // send WKS image
                            responseHeaders.set("Content-Type", "image/svg+xml");
                            String program = null;
                            try {
                                program = WebInterface.getDotPath();
                            } catch (Exception e) {}
                            //for (String s: exchange.getRequestHeaders().keySet())
                            //    System.out.println("* "+s+" = "+exchange.getRequestHeaders().getFirst(s));
                            if (program != null) {
                                String dot = getWksAsDot(id);
                                if (dot != null && !dot.isEmpty()) {
                                    File fin         = File.createTempFile("jacamo-e-", ".dot");
                                    File imgFile = File.createTempFile("jacamo-e-", ".svg");

                                    FileWriter out = new FileWriter(fin);
                                    out.append(dot);
                                    out.close();
                                    Process p = Runtime.getRuntime().exec(program+" -Tsvg "+fin.getAbsolutePath()+" -o "+imgFile.getAbsolutePath());
                                    try {
                                        p.waitFor(2000,TimeUnit.MILLISECONDS);

                                        byte[] imgData = new byte[(int)imgFile.length()];
                                        FileInputStream in = new FileInputStream(imgFile);
                                        in.read(imgData);
                                        in.close();

                                        //responseHeaders.set("Last-Modified", new Date( lastImgFile.lastModified()).toGMTString() );
                                        //responseHeaders.set("Cache-control", "max-age=2" );
                                        //responseHeaders.set("ETag", "x"+lastImgFile.hashCode());
                                        //exchange.sendResponseHeaders(304, -1);
                                        responseBody.write(imgData);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        } else {
                            // send artifact HTML
                            responseHeaders.set("Content-Type", "text/html");
                            try {
                                String path = exchange.getRequestURI().getPath();
                                int p = path.lastIndexOf("/");
                                path = path.substring(p+1);
    
                                ArtifactInfo info = CartagoService.getController(id).getArtifactInfo(path);
                                responseBody.write(getArtHtml(id, info).getBytes());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    responseBody.close();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    static String getWksAsDot(String wksName) {
        // TODO: implement it
        return "digraph G {\n" + 
                "\n" + 
                "	subgraph cluster_0 {\n" + 
                "		style=filled;\n" + 
                "		color=lightgrey;\n" + 
                "		node [style=filled,color=white];\n" + 
                "		a0 -> a1 -> a2 -> a3;\n" + 
                "		label = \"process #1\";\n" + 
                "	}\n" + 
                "\n" + 
                "	subgraph cluster_1 {\n" + 
                "		node [style=filled];\n" + 
                "		b0 -> b1 -> b2 -> b3;\n" + 
                "		label = \"process #2\";\n" + 
                "		color=blue\n" + 
                "	}\n" + 
                "	start -> a0;\n" + 
                "	start -> b0;\n" + 
                "	a1 -> b3;\n" + 
                "	b2 -> a3;\n" + 
                "	a3 -> a0;\n" + 
                "	a3 -> end;\n" + 
                "	b3 -> end;\n" + 
                "\n" + 
                "	start [shape=Mdiamond];\n" + 
                "	end [shape=Msquare];\n" + 
                "}";
    }
    
}
