package org.dcache.simplenfs;

import org.dcache.nfs.ExportFile;
import org.dcache.nfs.v3.MountServer;
import org.dcache.nfs.v3.NfsServerV3;
import org.dcache.nfs.v3.xdr.mount_prot;
import org.dcache.nfs.v3.xdr.nfs3_prot;
import org.dcache.nfs.v4.DeviceManager;
import org.dcache.nfs.v4.MDSOperationFactory;
import org.dcache.nfs.v4.NFSServerV41;
import org.dcache.nfs.v4.xdr.nfs4_prot;
import org.dcache.nfs.vfs.VirtualFileSystem;
import org.dcache.xdr.OncRpcProgram;
import org.dcache.xdr.OncRpcSvc;
import org.dcache.xdr.OncRpcSvcBuilder;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

public class SimpleNfsServer implements Closeable {
    private final OncRpcSvc nfsSvc;
    private final Path root;
    private final int port;
    private final String name;

    public SimpleNfsServer(Path root) {
        this(null, root, null, null);
    }

    public SimpleNfsServer(Integer port, Path root, ExportFile exportFile, String name) {
        try {
            if (exportFile == null) {
                exportFile = new ExportFile(new InputStreamReader(SimpleNfsServer.class.getClassLoader().getResourceAsStream("exports")));
            }

            if (port == null) {
                port = 2049;
            }
            this.port = port;

            if (root == null) {
                root = Files.createTempDirectory(null);
            }
            this.root = root;

            if (name == null) {
                name = "nfs@" + this.port;
            }
            this.name = name;

            VirtualFileSystem vfs = new LocalFileSystem(this.root, exportFile.getExports().collect(Collectors.toList()));

            nfsSvc = new OncRpcSvcBuilder()
                    .withPort(this.port)
                    .withTCP()
                    .withAutoPublish()
                    .withWorkerThreadIoStrategy()
                    .withServiceName(this.name)
                    .build();

            NFSServerV41 nfs4 = new NFSServerV41(
                    new MDSOperationFactory(),
                    new DeviceManager(),
                    vfs,
                    exportFile);

            NfsServerV3 nfs3 = new NfsServerV3(exportFile, vfs);
            MountServer mountd = new MountServer(exportFile, vfs);

            nfsSvc.register(new OncRpcProgram(mount_prot.MOUNT_PROGRAM, mount_prot.MOUNT_V3), mountd);
            nfsSvc.register(new OncRpcProgram(nfs3_prot.NFS_PROGRAM, nfs3_prot.NFS_V3), nfs3);
            nfsSvc.register(new OncRpcProgram(nfs4_prot.NFS4_PROGRAM, nfs4_prot.NFS_V4), nfs4);
            nfsSvc.start();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() throws IOException {
        nfsSvc.stop();
    }

    public Path getRoot() {
        return root;
    }

    public int getPort() {
        return port;
    }
}
