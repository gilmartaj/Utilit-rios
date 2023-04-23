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
* Nesta versão, cria um arquivo do tamanho total do arquivo a ser baixado e vai pŕeenchendo
* com os bytes descarregados
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
            File arquivoASerBaixado = new File(URL_ARQUIVO);
            String nomeArquivo = null;
            
            try{
                if(args.length > 1)
                    threads = Integer.valueOf(args[1]);
            }catch(Exception e){}
            
            if(threads < 1 || threads > 10)
                threads = 1;
            
            HttpURLConnection conexao = (HttpURLConnection) url.openConnection();
            
            conexao.setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:109.0) Gecko/20100101 Firefox/112.0");
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
            
            try{
                Baixador.prop.load(new FileInputStream(nomeArquivo+".download.properties"));
            }catch(Exception e){/*System.out.println("Erro ao carregar");*/}
            RandomAccessFile raf = new RandomAccessFile(nomeArquivo, "rw");
            raf.setLength(tamanhoTotalArquivo);
            raf.close();
            
            //System.exit(0);
            
            Exibidor exibidor = new Exibidor(tamanhoTotalArquivo);
            
            Baixador[] baixadores = new Baixador[threads];
            
            for(int i = 0; i < threads-1; i++)
            {
                baixadores[i] = new Baixador(URL_ARQUIVO, tamanhoTotalArquivo / threads * i, tamanhoTotalArquivo / threads * (i+1) - 1,""/* "p"+i*/, exibidor, nomeArquivo/* + "p"+i*/,i);
            }
            
            baixadores[threads-1] = new Baixador(URL_ARQUIVO, tamanhoTotalArquivo / (threads) * (threads-1), tamanhoTotalArquivo, ""/*"p"+(threads-1)*/, exibidor, nomeArquivo /*+ "p"+(threads-1)*/,(threads-1));
            
            System.out.println("\n"+nomeArquivo);
            
            System.out.println("\n Tamanho total: " + tamanhoTotalArquivo + " Bytes = " + tamanhoTotalArquivo/Exibidor.MEGABYTE + " MB");
            System.out.println("\n Baixado: " + 0 + " Bytes = " + 0 + " MB");
            
            for(int i = 0; i < threads; i++)
            {
                baixadores[i].start();
            }
            
            if(threads == 1)
            {
                baixadores[0].join();
                try{Thread.sleep(5);}catch(Exception e){}
                System.exit(0);
                new File(nomeArquivo + "p0").renameTo(new File(nomeArquivo));
            }
            else
            {
                long skip = 0;
                
                for(int i = 0; i < threads; i++)
                {
                    baixadores[i].join();
                }
                
                System.exit(0);
                for(int i = 0; i < threads; i++)
                {
                    //baixadores[i].join();
                    try{Thread.sleep(5);}catch(Exception e){}
                    
                    File f = new File(nomeArquivo + "p" + i);
                    try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(f)))
                    {
                        in.skip(skip);
                        OutputStream os = new FileOutputStream(nomeArquivo, true);
                        byte dataBuffer[] = new byte[1048576];
                        int bytesRead;
                        while ((bytesRead = in.read(dataBuffer, 0, 1048576)) != -1) 
                        {
                            os.write(dataBuffer, 0, bytesRead);
                                //exibidor.atualizar();
                            try {
                                //Thread.sleep(1L);
                            } catch (Exception exception) {}
                        }
                        
                        skip = f.length() - tamanhoTotalArquivo / threads;
                        f.delete();
                        
                    } catch (IOException e) 
                    {
                        e.printStackTrace();
                    }
                }
            
            }
           
        }catch(Exception e)
        {
            e.printStackTrace();
        }
    }
}

class Baixador extends Thread
{
    static Properties prop = new Properties();
    static{
    
    }
    private URL url;
    private long tamanho;
    private long inicio;
    private long fim;
    String nomeArquivo;
    HttpURLConnection conexao;
    private long tentativas = 10;
    private Exibidor exibidor;
    private long descarregado = 0;
    private int cod;
    
    public Baixador(String urlArquivo, long byteInicial, long byteFinal, String sufixo, Exibidor exibidor, String nome, int cod) throws Exception
    {
        this.url = new URL(urlArquivo);
        File arquivoASerBaixado = new File(urlArquivo);
        this.inicio = byteInicial;
        this.fim = byteFinal;
        this.exibidor = exibidor;
        this.cod = cod;
        descarregado = Long.valueOf(Baixador.prop.getProperty("t"+cod, "0"));
        
        tamanho = fim - inicio;
        
        this.conexao = (HttpURLConnection) url.openConnection();
        conexao.setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:109.0) Gecko/20100101 Firefox/112.0");
            
            nomeArquivo = nome;     
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
        File arquivo = new File(nomeArquivo);
        //Path hugeFile = Paths.get(nomeArquivo);
        //OpenOption[] options = { StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW , StandardOpenOption.SPARSE };
        
        exibidor.atualizar(descarregado);
        exibidor.atualizar(0);
    
        while(descarregado < tamanho){
                conexao.disconnect();
                conexao = (HttpURLConnection) url.openConnection();
                conexao.setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:109.0) Gecko/20100101 Firefox/112.0");
                //if(arquivo.exists())
                    conexao.setRequestProperty("Range", "bytes=" + (inicio+descarregado /*+ arquivo.length()*/) + "-" + fim);
                
                //synchronized
                //{
                    //if (tentativas > 0)
/* 43 */            //System.out.print(String.format("%c[9A\r%c[0J", new Object[] { Integer.valueOf(27), Integer.valueOf(27) })); 
                
                //while(conexao.getContentLength() == 0)Thread.sleep(5000);
                    //System.out.println("\n Tamanho total: " + tamanhoTotalArquivo + " Bytes = " + tamanhoTotalArquivo/1048576 + " MB");
                    //System.out.println("\n Por baixar: " + conexao.getContentLength() + 
                                       //" Bytes = " + conexao.getContentLength()/1048576 + " MB");
                    //System.out.println("\n" + arquivoSalvo.getName());
                System.out.println("aqui "+inicio);
                
                
                try (BufferedInputStream in = new BufferedInputStream(conexao.getInputStream()))
                {
                    RandomAccessFile raf = new RandomAccessFile(nomeArquivo, "rw");
                    //raf.setLength(fim-inicio);
                    //raf.close();
                    //FileOutputStream os = new FileOutputStream(arquivo.getName(), true);
                    //FileChannel ch = os.getChannel();
                    //ch.position(0);
                    raf.seek(inicio+descarregado);
                    byte dataBuffer[] = new byte[1048576];
                    int bytesRead;
                    ByteBuffer byteBuffer;
                    while ((bytesRead = in.read(dataBuffer, 0, 1048576)) > 0) 
                    {
                        //System.out.println("...");
                        //byteBuffer = ByteBuffer.wrap(dataBuffer);
                        //os.write(dataBuffer, 0, bytesRead);
                        //ch.write(byteBuffer);
                        raf.write(dataBuffer, 0, bytesRead);
                            exibidor.atualizar(bytesRead);
                            //ch.position(exibidor.baixado);
                            descarregado += bytesRead;
                            Baixador.prop.setProperty("t"+cod, descarregado+"");
                            try{Baixador.prop.store(new FileOutputStream(nomeArquivo+".download.properties"), "");}catch(Exception e){}
                        try {
                            Thread.sleep(1L);
                        } catch (Exception exception) {}
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
     long baixado;
    private long total;
    public static final long MEGABYTE = 1048576L;
    public static final char ESC_ASCII = 27;
    
    public Exibidor(long total)
    {
        this.total = total;
    }
    
    /*public synchronized void atualizar(long bytes)
    {
        baixado+=bytes;
        if(baixado % 50 == 0)
        {
            System.out.printf("%c[4A\r%c[0J", ESC_ASCII, ESC_ASCII); 
            System.out.println("\n Tamanho total: " + total + " Bytes = " + total/MEGABYTE + " MB");
            System.out.println("\n Baixado: " + baixado * 1024 + " Bytes = " + baixado + " MB");
        }
    }*/
    
    public synchronized void atualizar(long bytes)
    {
        baixado+=bytes;
            System.out.printf("%c[4A\r%c[0J", ESC_ASCII, ESC_ASCII); 
            System.out.println("\n Tamanho total: " + total + " Bytes = " + total/MEGABYTE + " MB");
            System.out.println("\n Baixado: " + baixado + " Bytes = " + baixado/MEGABYTE + " MB");
    }
    
}
