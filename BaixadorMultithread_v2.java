import java.net.*;
import java.io.*;
import java.util.Random;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.util.Properties;

/**
*
* Baixador multithread / continua "downloads" de onde parou
* Versão 2
* Nesta versão, cria um arquivo no disco do tamanho total do arquivo a ser baixado
* e vai pŕeenchendo com os bytes descarregados
* Na versão 1, eram baixados arquivos separados e depois juntados em um novo arquivo
*
* Compilação: javac BaixadorMultithread_v2.java
* Execução: java BaixadorMultithread_v2 [quantidade_threads] [nome_arquivo]
*
*/
public class BaixadorMultithread_v2{
    public static int MAXIMO_TENTATIVAS = 100;
    
    public static void main(String[] args)
    {
        int baixado = 0;
        int tentativas = 0;
        try
        {
            int threads = 0;
            final String URL_ARQUIVO = args[0];
            URL url = new URL(URL_ARQUIVO);
            String nomeArquivo = null;
            
            try{
                if(args.length > 1)
                    threads = Integer.valueOf(args[1]);
            }catch(Exception e){}
            
            if(threads < 1 || threads > 10)
                threads = 1;
            
            HttpURLConnection conexao = (HttpURLConnection) url.openConnection();
            
            conexao.setRequestProperty("User-Agent", Baixador.USER_AGENT);
            //con.setRequestMethod("HEAD");
            long tamanhoTotalArquivo = conexao.getContentLengthLong();
            conexao.disconnect();
            
            if(tamanhoTotalArquivo <= 0)
            {
                System.out.println("\nERRO: Não foi possível determinar o tamanho do arquivo.");
                System.exit(1);
            }
            
            if(args.length > 2)
                nomeArquivo = args[2];
            else
            {
                nomeArquivo = conexao.getHeaderField("Content-Disposition");
                if(nomeArquivo != null && nomeArquivo.contains("filename=\""))
                {
                    nomeArquivo = new String(nomeArquivo.getBytes("ISO-8859-1"));
                    
                    nomeArquivo = nomeArquivo.substring(nomeArquivo.indexOf("filename=\"")+10);
                    
                    nomeArquivo = nomeArquivo.substring(0, nomeArquivo.indexOf("\""));
                    nomeArquivo = nomeArquivo.replace("%20", " ");
                }
                else
                    nomeArquivo = "Nome_nao_identificado_" + new Random().nextLong() + "_" + new Random().nextInt(); 
            }
            
            try{Baixador.prop.load(new FileInputStream(nomeArquivo+".download.properties"));}catch(Exception e){}
            
            File arquivoASerBaixado = new File(nomeArquivo);
            
            if(!arquivoASerBaixado.exists())
            {
                RandomAccessFile raf = new RandomAccessFile(nomeArquivo, "rw");
                raf.setLength(tamanhoTotalArquivo);
                raf.close();
            }
            else
            {
                if(arquivoASerBaixado.length() != tamanhoTotalArquivo)
                {
                     System.out.println("\nERRO: Arquivo já existe com tamanho diferente.");
                     System.exit(2);
                }
            }
            
            Exibidor exibidor = new Exibidor(tamanhoTotalArquivo);
            
            Baixador[] baixadores = new Baixador[threads];
            
            for(int i = 0; i < threads-1; i++)
            {
                baixadores[i] = new Baixador(URL_ARQUIVO, tamanhoTotalArquivo / threads * i, tamanhoTotalArquivo / threads * (i+1) - 1, exibidor, nomeArquivo, i);
            }
            
            baixadores[threads-1] = new Baixador(URL_ARQUIVO, tamanhoTotalArquivo / threads * (threads-1), tamanhoTotalArquivo, exibidor, nomeArquivo, threads-1);
            
            System.out.println("\n"+nomeArquivo);
            System.out.println("\n Tamanho total: " + tamanhoTotalArquivo + " Bytes = " + tamanhoTotalArquivo/Exibidor.MEGABYTE + " MB");
            System.out.println("\n Baixado: " + 0 + " Bytes = " + 0 + " MB");
            
            for(int i = 0; i < threads; i++)
            {
                baixadores[i].start();
                baixadores[i].join();
            }
            
            new File(nomeArquivo+".download.properties").delete();
           
        }catch(Exception e)
        {
            e.printStackTrace();
        }
    }
}

class Baixador extends Thread
{
    public static final int MEGABYTE = 1048576;
    public static final String USER_AGENT = "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:109.0) Gecko/20100101 Firefox/112.0";

    static Properties prop = new Properties(); 

    private URL url;
    private long tamanho;
    private long inicio;
    private long fim;
    private String nomeArquivo;
    private HttpURLConnection conexao;
    private long tentativas = 10;
    private Exibidor exibidor;
    private long descarregado;
    private String identificador;
    
    public Baixador(String urlArquivo, long byteInicial, long byteFinal, Exibidor exibidor, String nomeArquivo, int numeroIdentificador) throws Exception
    {
        this.url = new URL(urlArquivo);
        this.inicio = byteInicial;
        this.fim = byteFinal;
        this.exibidor = exibidor;
        this.identificador = "p" + numeroIdentificador;
        this.descarregado = Long.valueOf(Baixador.prop.getProperty(identificador, "0"));
        this.nomeArquivo = nomeArquivo; 
        
        tamanho = fim - inicio;
        
        this.conexao = (HttpURLConnection) url.openConnection();
        conexao.setRequestProperty("User-Agent", Baixador.USER_AGENT);    
    }
    
    @Override
    public void run()
    {
        try
        {
            baixar();
        }
        catch(Exception e)
        {
            e.printStackTrace();
            System.out.print("Erro ao baixar.");
            System.exit(1);
        }
    }
    
    public void baixar() throws IOException
    {   
        exibidor.atualizar(descarregado);
    
        while(descarregado < tamanho)
        {
            conexao.disconnect();
            conexao = (HttpURLConnection) url.openConnection();
            conexao.setRequestProperty("User-Agent", Baixador.USER_AGENT);

            conexao.setRequestProperty("Range", "bytes=" + (inicio + descarregado) + "-" + fim);
            
            try (BufferedInputStream in = new BufferedInputStream(conexao.getInputStream()))
            {
                RandomAccessFile raf = new RandomAccessFile(nomeArquivo, "rw");
                raf.seek(inicio + descarregado);
                byte dataBuffer[] = new byte[Baixador.MEGABYTE];
                int bytesRead;
                ByteBuffer byteBuffer;
                while ((bytesRead = in.read(dataBuffer, 0, Baixador.MEGABYTE)) > 0) 
                {
                    raf.write(dataBuffer, 0, bytesRead);
                    exibidor.atualizar(bytesRead);
                    descarregado += bytesRead;
                    Baixador.prop.setProperty(identificador, String.valueOf(descarregado));
                    try{Baixador.prop.store(new FileOutputStream(nomeArquivo+".download.properties"), "");}catch(Exception e){}
                    try {Thread.sleep(1L);}catch(Exception e){}
                }
                
            } catch (IOException e) 
            {
                e.printStackTrace();
            }
            //tentativas++;
        }
        conexao.disconnect();
    }
}

class Exibidor
{
    private long baixado;
    private long total;
    public static final long MEGABYTE = 1048576L;
    public static final char ESC_ASCII = 27;
    
    public Exibidor(long total)
    {
        this.total = total;
    }
    
    public synchronized void atualizar(long bytes)
    {
        baixado+=bytes;
        System.out.printf("%c[4A\r%c[0J", ESC_ASCII, ESC_ASCII); 
        System.out.println("\n Tamanho total: " + total + " bytes = " + total/Baixador.MEGABYTE + " MB");
        System.out.println("\n Baixado: " + baixado + " bytes = " + baixado/Baixador.MEGABYTE + " MB");
    }
    
}
