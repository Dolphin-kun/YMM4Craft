namespace YMM4Craft.Core.Protocol;

public sealed class CraftCommandException(string command, string message) : Exception(message)
{
    public string Command { get; } = command;
}
