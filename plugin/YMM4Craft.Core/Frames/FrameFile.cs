using System.IO.MemoryMappedFiles;

namespace YMM4Craft.Core.Frames;

public sealed class FrameFile : IDisposable
{
    public const int Magic = 0x46433459;
    public const int LayoutVersion = 2;
    public const int HeaderSize = 64;
    public const int SlotHeaderSize = 64;

    public const long DefaultCapacity = HeaderSize + SlotHeaderSize + 3840L * 2160L * 4L + 4096;

    readonly FileStream stream;
    readonly MemoryMappedFile map;
    readonly MemoryMappedViewAccessor view;

    public string Path { get; }
    public long Capacity { get; }

    FrameFile(string path, long capacity, FileStream stream, MemoryMappedFile map, MemoryMappedViewAccessor view)
    {
        Path = path;
        Capacity = capacity;
        this.stream = stream;
        this.map = map;
        this.view = view;
    }

    public static FrameFile Create(string path, long capacity)
    {
        var stream = new FileStream(path, FileMode.Create, FileAccess.ReadWrite, FileShare.ReadWrite | FileShare.Delete);
        try
        {
            stream.SetLength(capacity);
            var map = MemoryMappedFile.CreateFromFile(stream, null, capacity, MemoryMappedFileAccess.ReadWrite, HandleInheritability.None, leaveOpen: true);
            var view = map.CreateViewAccessor(0, capacity, MemoryMappedFileAccess.Read);
            return new FrameFile(path, capacity, stream, map, view);
        }
        catch
        {
            stream.Dispose();
            throw;
        }
    }

    public Frame? Read(FrameNotice notice) => Read(notice, null);

    public Frame? Read(FrameNotice notice, byte[]? buffer)
    {
        if (view.ReadInt32(0) != Magic || view.ReadInt32(4) != LayoutVersion) return null;
        var offset = notice.Offset;
        if (offset < HeaderSize || offset + SlotHeaderSize > Capacity) return null;
        if (view.ReadInt64(offset) != notice.Sequence) return null;

        var width = view.ReadInt32(offset + 8);
        var height = view.ReadInt32(offset + 12);
        var stride = view.ReadInt32(offset + 16);
        var format = (FrameFormat)view.ReadInt32(offset + 20);
        var revision = view.ReadInt64(offset + 24);
        var settled = view.ReadInt32(offset + 32) != 0;
        if (!Enum.IsDefined(format) || width <= 0 || height <= 0 || stride < width * 4) return null;
        if (offset + SlotHeaderSize + (long)stride * height > Capacity) return null;

        var pixels = buffer is not null && buffer.Length == stride * height ? buffer : new byte[stride * height];
        CopyOut(offset + SlotHeaderSize, pixels);

        if (view.ReadInt64(offset) != notice.Sequence) return null;
        return new Frame(notice.Sequence, width, height, stride, format, revision, pixels) { Settled = settled };
    }

    unsafe void CopyOut(long position, byte[] destination)
    {
        byte* basePtr = null;
        var handle = view.SafeMemoryMappedViewHandle;
        handle.AcquirePointer(ref basePtr);
        try
        {
            var src = basePtr + view.PointerOffset + position;
            fixed (byte* dst = destination)
                Buffer.MemoryCopy(src, dst, destination.Length, destination.Length);
        }
        finally
        {
            handle.ReleasePointer();
        }
    }

    public void Dispose()
    {
        view.Dispose();
        map.Dispose();
        stream.Dispose();
        try { File.Delete(Path); } catch (IOException) { } catch (UnauthorizedAccessException) { }
    }
}
