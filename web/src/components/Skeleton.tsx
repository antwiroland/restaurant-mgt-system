"use client";

type SkeletonProps = {
  className?: string;
};

export function Skeleton({ className = "" }: SkeletonProps) {
  return <div className={`skeleton rounded-[var(--radius-md)] ${className}`} aria-hidden="true" />;
}

