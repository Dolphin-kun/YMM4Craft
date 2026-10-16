namespace YMM4Craft.Controls;

internal static class Angles
{
    public const double Limit = 100000;

    public static double Wrap(double degrees) => double.IsFinite(degrees) ? Math.IEEERemainder(degrees, 360) : 0;
}
