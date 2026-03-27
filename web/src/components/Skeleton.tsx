"use client";

type SkeletonProps = {
  className?: string;
};

export function Skeleton({ className = "" }: SkeletonProps) {
  return <div className={`animate-pulse rounded-[var(--radius-md)] bg-muted ${className}`} aria-hidden="true" />;
}

