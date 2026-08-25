const nextConfig = {
  experimental: {
    forceSwcTransforms: false,
  },
  swcMinify: false,
  eslint: {
    ignoreDuringBuilds: true,
  },
  typescript: {
    ignoreBuildErrors: true,
  },
};

module.exports = nextConfig;

